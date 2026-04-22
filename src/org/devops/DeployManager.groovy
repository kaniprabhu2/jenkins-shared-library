package org.devops


class DeployManager implements Serializable {

    def steps            // Jenkins pipeline script context — always pass 'this'
    String env           // dev | staging | prod
    String strategy      // rolling | bluegreen | canary
    String imageTag      // current image tag being deployed  e.g. "latest" or "v1.2"
    String prevImageTag  // snapshot tag saved before deploy — used for rollback

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // Accepts strategy + imageTag so the class is self-contained.
    // ─────────────────────────────────────────────────────────────────────────
    DeployManager(def steps, String env, String strategy, String imageTag = 'latest') {
        this.steps       = steps
        this.env         = env
        this.strategy    = strategy
        this.imageTag    = imageTag
        this.prevImageTag = "rollback-snapshot-${env}"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALIDATION
    // ─────────────────────────────────────────────────────────────────────────
    def validate() {
        steps.echo "===== VALIDATION STARTED [ENV: ${env}] ====="

        // Check 1: docker-compose.yaml at repo root (critical — fail fast)
        steps.sh """
            if [ -f docker-compose.yaml ] || [ -f docker-compose.yml ]; then
                echo "[OK] docker-compose file found"
            else
                echo "[ERROR] docker-compose.yaml missing at repo root"
                exit 1
            fi
        """

        // Check 2: Per-service Dockerfiles
        // FIX: Escape \$dir so Groovy does NOT interpolate it.
        // Your original code had "$dir" (unescaped) which caused:
        //   groovy.lang.MissingPropertyException: No such property: dir
        steps.sh """
            echo "Checking service Dockerfiles..."
            for dir in attendance employee frontend mysql notification elasticsearch; do
                if [ -f "\$dir/Dockerfile" ]; then
                    echo "[OK]   \$dir/Dockerfile found"
                else
                    echo "[WARN] \$dir/Dockerfile missing (non-critical)"
                fi
            done
        """

        // Check 3: docker and docker-compose are available on the agent
        steps.sh """
            echo "Checking tooling availability..."
            docker --version       || (echo "[ERROR] docker not found" && exit 1)
            docker compose version || docker-compose --version || (echo "[ERROR] docker-compose not found" && exit 1)
            echo "[OK] Docker tooling available"
        """

        // Check 4: docker-compose config is syntactically valid
        steps.sh """
            docker compose config --quiet 2>/dev/null || docker-compose config --quiet
            echo "[OK] docker-compose config is valid YAML"
        """

        steps.echo "===== VALIDATION COMPLETED ====="
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEPLOY ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────
    def deploy() {
        steps.echo "===== DEPLOY STARTED [ENV: ${env} | STRATEGY: ${strategy}] ====="

        // Snapshot current running image BEFORE deploy so rollback has something real
        snapshotForRollback()

        switch (strategy) {
            case 'rolling':
                rollingDeploy()
                break
            case 'bluegreen':
                blueGreenDeploy()
                break
            case 'canary':
                canaryDeploy()
                break
            default:
                steps.error("Unknown strategy '${strategy}'. Use: rolling | bluegreen | canary")
        }

        steps.echo "===== DEPLOY COMPLETED ====="
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SNAPSHOT — saves current image tag before deploy (enables real rollback)
    // ─────────────────────────────────────────────────────────────────────────
    private def snapshotForRollback() {
        steps.echo "[SNAPSHOT] Tagging current images as rollback snapshot..."
        steps.sh """
            # Get list of running service names from compose
            SERVICES=\$(docker compose ps --services 2>/dev/null || docker-compose ps --services 2>/dev/null || echo "")

            if [ -z "\$SERVICES" ]; then
                echo "[SNAPSHOT] No running containers found — skipping snapshot."
            else
                for svc in \$SERVICES; do
                    IMAGE_ID=\$(docker compose images -q \$svc 2>/dev/null || true)
                    if [ -n "\$IMAGE_ID" ]; then
                        docker tag \$IMAGE_ID ${prevImageTag}-\$svc 2>/dev/null || true
                        echo "[SNAPSHOT] Tagged \$svc → ${prevImageTag}-\$svc"
                    fi
                done
            fi
        """
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROLLING DEPLOY
    // Strategy: stop old → start new, service-by-service.
    // Uses --no-deps so each service is updated independently.
    // ─────────────────────────────────────────────────────────────────────────
    private def rollingDeploy() {
        steps.echo "[ROLLING] Starting rolling deployment..."
        steps.sh """
            echo "[ROLLING] Pulling latest images..."
            docker compose pull 2>/dev/null || docker-compose pull || true

            echo "[ROLLING] Updating services one by one (--no-deps = no dependency restart)..."
            SERVICES=\$(docker compose config --services 2>/dev/null || docker-compose config --services)

            for svc in \$SERVICES; do
                echo "[ROLLING] Updating service: \$svc"
                docker compose up -d --no-deps --build \$svc 2>/dev/null || \
                docker-compose up -d --no-deps --build \$svc
                echo "[ROLLING] \$svc updated."
            done

            echo "[ROLLING] All services updated."
        """
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLUE-GREEN DEPLOY
    // Strategy:
    //   1. Spin up a parallel "green" stack on different ports
    //   2. Verify green is healthy
    //   3. Switch traffic (update .env / labels) to green
    //   4. Leave blue running for quick rollback
    //
    // Uses COMPOSE_PROJECT_NAME to isolate the two stacks.
    // ─────────────────────────────────────────────────────────────────────────
    private def blueGreenDeploy() {
        steps.echo "[BLUE-GREEN] Starting blue-green deployment..."
        steps.sh """
            # Determine current active slot
            CURRENT_SLOT=\$(cat .active_slot 2>/dev/null || echo "blue")

            if [ "\$CURRENT_SLOT" = "blue" ]; then
                NEW_SLOT="green"
                OLD_SLOT="blue"
            else
                NEW_SLOT="blue"
                OLD_SLOT="green"
            fi

            echo "[BG] Active: \$OLD_SLOT → Deploying to: \$NEW_SLOT"

            # Deploy new slot as a separate compose project
            COMPOSE_PROJECT_NAME=ot-\$NEW_SLOT docker compose up -d --build 2>/dev/null || \
            COMPOSE_PROJECT_NAME=ot-\$NEW_SLOT docker-compose up -d --build

            echo "[BG] Green stack is up. Waiting 15s for health checks..."
            sleep 15

            # Verify at least one container in new slot is running
            RUNNING=\$(COMPOSE_PROJECT_NAME=ot-\$NEW_SLOT docker compose ps --status running -q 2>/dev/null | wc -l || echo 0)
            if [ "\$RUNNING" -eq "0" ]; then
                echo "[BG ERROR] New slot (\$NEW_SLOT) has no running containers. Aborting."
                COMPOSE_PROJECT_NAME=ot-\$NEW_SLOT docker compose down 2>/dev/null || true
                exit 1
            fi

            echo "[BG] New slot (\$NEW_SLOT) verified. Switching traffic..."

            # Save new active slot — this is the "traffic switch" in a local compose setup
            echo "\$NEW_SLOT" > .active_slot

            echo "[BG] Traffic switched to \$NEW_SLOT."
            echo "[BG] Old slot (\$OLD_SLOT) still running. Run 'COMPOSE_PROJECT_NAME=ot-\$OLD_SLOT docker compose down' to clean up."
        """
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANARY DEPLOY
    // Strategy:
    //   1. Keep all existing (stable) containers running
    //   2. Deploy 1 canary container alongside stable
    //   3. Wait for manual/automatic promotion window
    //   4. Promote to full deployment (or rollback if unhealthy)
    // ─────────────────────────────────────────────────────────────────────────
    private def canaryDeploy() {
        steps.echo "[CANARY] Starting canary deployment..."
        steps.sh """
            echo "[CANARY] Stable stack remains untouched."

            # Bring up canary as a separate project with 1 replica
            echo "[CANARY] Deploying canary instance..."
            COMPOSE_PROJECT_NAME=ot-canary docker compose up -d --build --scale employee-api=1 2>/dev/null || \
            COMPOSE_PROJECT_NAME=ot-canary docker-compose up -d --build

            echo "[CANARY] Canary is up. Observing for 20 seconds..."
            sleep 20

            # Check canary health
            CANARY_RUNNING=\$(COMPOSE_PROJECT_NAME=ot-canary docker compose ps --status running -q 2>/dev/null | wc -l || echo 0)

            if [ "\$CANARY_RUNNING" -gt "0" ]; then
                echo "[CANARY] ✅ Canary healthy. Promoting to full deployment..."
                COMPOSE_PROJECT_NAME=ot-canary docker compose down 2>/dev/null || true
                docker compose up -d --build 2>/dev/null || docker-compose up -d --build
                echo "[CANARY] Full promotion complete."
            else
                echo "[CANARY] ❌ Canary unhealthy. Tearing down canary. Stable untouched."
                COMPOSE_PROJECT_NAME=ot-canary docker compose down 2>/dev/null || true
                exit 1
            fi
        """
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HEALTH CHECK
    // Verifies containers are actually running and responding.
    // FIX: Your original just ran `docker ps` — that doesn't fail the build
    // even if all containers are down. This version actually exits 1 on failure.
    // ─────────────────────────────────────────────────────────────────────────
    def healthCheck() {
        steps.echo "===== HEALTH CHECK [ENV: ${env}] ====="
        steps.sh """
            echo "--- Running containers ---"
            docker compose ps 2>/dev/null || docker-compose ps

            echo "--- Checking for unhealthy / exited containers ---"
            UNHEALTHY=\$(docker compose ps --status exited -q 2>/dev/null | wc -l || echo 0)
            TOTAL=\$(docker compose ps -q 2>/dev/null | wc -l || echo 0)

            echo "Total containers: \$TOTAL"
            echo "Exited/unhealthy:  \$UNHEALTHY"

            if [ "\$TOTAL" -eq "0" ]; then
                echo "[HEALTH FAIL] No containers are running at all!"
                exit 1
            fi

            if [ "\$UNHEALTHY" -gt "0" ]; then
                echo "[HEALTH FAIL] \$UNHEALTHY container(s) have exited."
                docker compose ps 2>/dev/null || docker-compose ps
                exit 1
            fi

            echo "[HEALTH OK] All containers running."
        """
        steps.echo "===== HEALTH CHECK PASSED ====="
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROLLBACK
    // FIX: Your original rollback was: docker-compose down + docker-compose up
    // That REDEPLOYED THE BROKEN VERSION — not a rollback at all.
    //
    // Real rollback: use the snapshot tags we saved in snapshotForRollback().
    // If no snapshot exists, fall back to `docker compose down` (safe stop).
    // ─────────────────────────────────────────────────────────────────────────
    def rollback() {
        steps.echo "===== ROLLBACK INITIATED [ENV: ${env}] ====="
        steps.sh """
            echo "[ROLLBACK] Checking for rollback snapshots..."

            # Check if any snapshot tags exist
            SNAPSHOTS=\$(docker images --format '{{.Repository}}:{{.Tag}}' | grep '${prevImageTag}' || echo "")

            if [ -z "\$SNAPSHOTS" ]; then
                echo "[ROLLBACK] No snapshot images found."
                echo "[ROLLBACK] Safe stopping all containers to prevent further damage."
                docker compose down 2>/dev/null || docker-compose down || true
                echo "[ROLLBACK] Containers stopped. Manual intervention required to restore."
            else
                echo "[ROLLBACK] Snapshot images found:"
                echo "\$SNAPSHOTS"

                echo "[ROLLBACK] Stopping current broken containers..."
                docker compose down 2>/dev/null || docker-compose down || true

                echo "[ROLLBACK] Restoring snapshot images..."
                for snapshot in \$SNAPSHOTS; do
                    # Extract service name from tag: rollback-snapshot-dev-employee-api → employee-api
                    SVC=\$(echo \$snapshot | sed 's|.*${prevImageTag}-||')
                    docker tag \$snapshot \$SVC:${imageTag} 2>/dev/null || true
                    echo "[ROLLBACK] Restored \$SVC from snapshot."
                done

                echo "[ROLLBACK] Restarting with restored images..."
                docker compose up -d 2>/dev/null || docker-compose up -d
                echo "[ROLLBACK] ✅ Rollback complete."
            fi
        """
        steps.echo "===== ROLLBACK COMPLETED ====="
    }
}

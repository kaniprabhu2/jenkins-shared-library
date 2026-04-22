package org.devops

class DeployManager implements Serializable {

    def steps
    String env
    String strategy
    String imageTag

    DeployManager(def steps, String env, String strategy, String imageTag = "latest") {
        this.steps = steps
        this.env = env
        this.strategy = strategy
        this.imageTag = imageTag
    }

    // =========================
    // VALIDATION
    // =========================
    def validate() {
        steps.echo "===== VALIDATION STARTED ====="
        steps.echo "ENV: ${env}"

        steps.sh "ls -l"

        // Critical check
        steps.sh '''
        if [ -f docker-compose.yaml ]; then
            echo "[OK] docker-compose.yaml found"
        else
            echo "[ERROR] docker-compose.yaml missing"
            exit 1
        fi
        '''

        // Safe Dockerfile check (NO Groovy interpolation issue)
        steps.sh '''
        echo "Checking service Dockerfiles..."
        for d in attendance employee frontend mysql notification elasticsearch; do
            if [ -f "$d/Dockerfile" ]; then
                echo "[OK] $d Dockerfile exists"
            else
                echo "[WARN] $d Dockerfile missing"
            fi
        done
        '''

        // Check docker availability
        steps.sh '''
        docker --version || exit 1
        docker-compose --version || exit 1
        '''

        steps.echo "===== VALIDATION COMPLETED ====="
    }

    // =========================
    // DEPLOY ENTRY
    // =========================
    def deploy() {
        steps.echo "===== DEPLOY STARTED ====="
        steps.echo "ENV: ${env} | STRATEGY: ${strategy}"

        switch(strategy) {
            case "rolling":
                rollingDeploy()
                break
            case "bluegreen":
                blueGreenDeploy()
                break
            case "canary":
                canaryDeploy()
                break
            default:
                steps.error "Invalid strategy"
        }
    }

    // =========================
    // ROLLING DEPLOY
    // =========================
    def rollingDeploy() {
        steps.echo "[ROLLING] Deployment"

        steps.sh '''
        cd salary

        echo "Pulling images..."
        docker-compose pull || true

        echo "Restarting services..."
        docker-compose down || true
        docker-compose up -d
        '''
    }

    // =========================
    // BLUE-GREEN (SIMPLIFIED)
    // =========================
    def blueGreenDeploy() {
        steps.echo "[BLUE-GREEN] Deployment (simplified)"

        steps.sh '''
        cd salary

        docker-compose pull || true

        echo "Simulating blue-green switch..."
        docker-compose up -d
        '''
    }

    // =========================
    // CANARY (SIMPLIFIED)
    // =========================
    def canaryDeploy() {
        steps.echo "[CANARY] Deployment (simplified)"

        steps.sh '''
        cd salary

        docker-compose pull || true

        echo "Deploying canary..."
        sleep 5

        docker-compose up -d
        '''
    }

    // =========================
    // HEALTH CHECK
    // =========================
    def healthCheck() {
        steps.echo "===== HEALTH CHECK ====="

        steps.sh '''
        docker ps
        '''
    }

    // =========================
    // ROLLBACK (SAFE)
    // =========================
    def rollback() {
        steps.echo "===== ROLLBACK ====="

        steps.sh '''
        cd salary

        echo "Stopping containers..."
        docker-compose down || true

        echo "Restarting last stable state..."
        docker-compose up -d || true
        '''
    }
}

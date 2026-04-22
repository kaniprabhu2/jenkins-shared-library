package org.devops

class DeployManager implements Serializable {

    def steps

    DeployManager(steps) {
        this.steps = steps
    }

    // =========================
    // VALIDATION
    // =========================
    def validate() {
        steps.echo "===== VALIDATION STARTED ====="

        steps.sh "ls -l"

        // Critical check
        steps.sh """
        if [ -f docker-compose.yaml ]; then
            echo "[OK] docker-compose.yaml found"
        else
            echo "[ERROR] docker-compose.yaml missing"
            exit 1
        fi
        """

        // Non-critical checks
        steps.sh """
        echo "Checking service Dockerfiles..."

        for dir in attendance employee frontend mysql notification elasticsearch; do
            if [ -f "$dir/Dockerfile" ]; then
                echo "[OK] $dir Dockerfile exists"
            else
                echo "[WARN] $dir Dockerfile missing"
            fi
        done
        """

        steps.echo "===== VALIDATION COMPLETED ====="
    }

    // =========================
    // DEPLOY ENTRY
    // =========================
    def deploy(String env, String strategy) {
        steps.echo "===== DEPLOYMENT STARTED ====="
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
                steps.error "Invalid deployment strategy"
        }
    }

    // =========================
    // ROLLING DEPLOYMENT
    // =========================
    def rollingDeploy() {
        steps.echo "Rolling deployment..."

        steps.sh """
        cd salary

        echo "Pulling latest images..."
        docker-compose pull || true

        echo "Stopping old containers..."
        docker-compose down || true

        echo "Starting new containers..."
        docker-compose up -d
        """
    }

    // =========================
    // BLUE-GREEN DEPLOYMENT
    // =========================
    def blueGreenDeploy() {
        steps.echo "Blue-Green deployment..."

        steps.sh """
        cd salary

        docker-compose pull || true

        echo "Deploying GREEN version..."
        docker-compose up -d

        echo "Traffic switch simulated"
        """
    }

    // =========================
    // CANARY DEPLOYMENT
    // =========================
    def canaryDeploy() {
        steps.echo "Canary deployment..."

        steps.sh """
        cd salary

        docker-compose pull || true

        echo "Deploying canary version..."
        sleep 5

        echo "Promoting full deployment..."
        docker-compose up -d
        """
    }

    // =========================
    // HEALTH CHECK
    // =========================
    def healthCheck() {
        steps.echo "===== HEALTH CHECK ====="

        steps.sh """
        docker ps
        """
    }

    // =========================
    // ROLLBACK
    // =========================
    def rollback() {
        steps.echo "===== ROLLBACK INITIATED ====="

        steps.sh """
        cd salary

        echo "Stopping containers..."
        docker-compose down || true

        echo "Restarting previous state..."
        docker-compose up -d || true
        """
    }
}

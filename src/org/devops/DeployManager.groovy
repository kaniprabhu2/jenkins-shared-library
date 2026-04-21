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
        steps.echo "Starting CI validation..."

        steps.sh "ls -l"

        // docker-compose check
        steps.sh """
        if [ -f docker-compose.yaml ]; then
            echo "docker-compose.yaml found"
        else
            echo "docker-compose.yaml missing!"
            exit 1
        fi
        """

        // Check Dockerfiles inside services
        steps.sh """
        echo "Checking service Dockerfiles..."

        for dir in attendance employee frontend mysql notification elasticsearch; do
            if [ -f "$dir/Dockerfile" ]; then
                echo "$dir Dockerfile exists"
            else
                echo "$dir Dockerfile missing"
            fi
        done
        """

        steps.echo "Validation successful"
    }

    // =========================
    // DEPLOY
    // =========================
    def deploy(String env, String strategy) {
        steps.echo "Deploying using ${strategy}"

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

    def rollingDeploy() {
        steps.sh """
        docker-compose down
        docker-compose up -d
        """
    }

    def blueGreenDeploy() {
        steps.sh """
        echo "Blue-Green deployment"
        docker-compose up -d
        """
    }

    def canaryDeploy() {
        steps.sh """
        echo "Canary deployment"
        sleep 5
        docker-compose up -d
        """
    }

    // =========================
    // HEALTH
    // =========================
    def healthCheck() {
        steps.sh "docker ps"
    }

    // =========================
    // ROLLBACK
    // =========================
    def rollback() {
        steps.echo "Rollback triggered"

        steps.sh """
        docker-compose down
        docker-compose up -d
        """
    }
}
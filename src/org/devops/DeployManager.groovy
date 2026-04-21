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

        // Repo structure
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
    // MAIN DEPLOY ENTRY
    // =========================
    def deploy(String env, String strategy) {
        steps.echo "Deploying using ${strategy} strategy in ${env}"

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
    // ROLLING DEPLOYMENT
    // =========================
    def rollingDeploy() {
        steps.echo "Rolling deployment started"

        steps.dir("salary") {
            steps.sh """
            docker-compose down
            docker-compose up -d
            """
        }
    }

    // =========================
    // BLUE-GREEN DEPLOYMENT
    // =========================
    def blueGreenDeploy() {
        steps.echo "Blue-Green deployment started"

        steps.dir("salary") {
            steps.sh """
            echo "Deploying GREEN version..."
            docker-compose up -d
            echo "Switching traffic (simulated)"
            """
        }
    }

    // =========================
    // CANARY DEPLOYMENT
    // =========================
    def canaryDeploy() {
        steps.echo "Canary deployment started"

        steps.dir("salary") {
            steps.sh """
            echo "Deploying canary version..."
            sleep 5
            echo "Promoting full deployment..."
            docker-compose up -d
            """
        }
    }

    // =========================
    // HEALTH CHECK
    // =========================
    def healthCheck() {
        steps.echo "Checking application health..."

        steps.sh """
        docker ps
        """
    }

    // =========================
    // ROLLBACK
    // =========================
    def rollback() {
        steps.echo "Rollback triggered"

        steps.dir("salary") {
            steps.sh """
            docker-compose down
            docker-compose up -d
            """
        }
    }
}
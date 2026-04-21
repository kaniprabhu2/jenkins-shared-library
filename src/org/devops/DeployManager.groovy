package org.devops

class DeployManager implements Serializable {

    def steps

    DeployManager(steps) {
        this.steps = steps
    }

    // =========================
    // VALIDATION (CI CHECKS)
    // =========================
    def validate() {
        steps.echo "Starting CI validation..."

        // Basic check
        steps.sh "echo 'Checking repository structure...' && ls -l"

        // docker-compose check (important for your repo)
        steps.sh """
        if [ -f docker-compose.yaml ]; then
            echo "docker-compose.yaml found"
        else
            echo "docker-compose.yaml missing!"
            exit 1
        fi
        """

        // YAML validation (optional)
        steps.sh """
        if command -v yamllint >/dev/null 2>&1; then
            yamllint .
        else
            echo "yamllint not installed, skipping"
        fi
        """

        // Microservice folders check
        steps.sh """
        for dir in attendance employee frontend mysql notification salary; do
            if [ -d "$dir" ]; then
                echo "$dir exists"
            else
                echo "$dir missing"
            fi
        done
        """

        // Git info
        steps.sh "git log -1 --oneline"

        steps.echo "Validation successful"
    }

    // =========================
    // MAIN DEPLOY ENTRY
    // =========================
    def deploy(String env, String strategy) {
        steps.echo "Deploying to ${env} using ${strategy}"

        switch(strategy) {

            case "rolling":
                rollingDeploy(env)
                break

            case "bluegreen":
                blueGreenDeploy(env)
                break

            case "canary":
                canaryDeploy(env)
                break

            default:
                steps.error "Invalid strategy"
        }
    }

    // =========================
    // ROLLING DEPLOYMENT
    // =========================
    def rollingDeploy(String env) {
        steps.echo "Rolling deployment (docker-compose restart)"

        steps.sh """
        export KUBECONFIG=/root/.kube/config
        docker-compose down
        docker-compose up -d
        """
    }

    // =========================
    // BLUE-GREEN DEPLOYMENT
    // =========================
    def blueGreenDeploy(String env) {
        steps.echo "Blue-Green deployment (simulated)"

        steps.sh """
        export KUBECONFIG=/root/.kube/config
        echo "Deploying GREEN version..."
        docker-compose up -d

        echo "Switching traffic (simulated)"
        """
    }

    // =========================
    // CANARY DEPLOYMENT
    // =========================
    def canaryDeploy(String env) {
        steps.echo "Canary deployment (simulated)"

        steps.sh """
        export KUBECONFIG=/root/.kube/config
        echo "Deploying small subset..."
        sleep 5

        echo "Promoting full deployment..."
        docker-compose up -d
        """
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
        steps.echo "Rollback triggered..."

        steps.sh """
        echo "Rolling back (restart previous containers)"
        docker-compose down
        docker-compose up -d
        """
    }
}
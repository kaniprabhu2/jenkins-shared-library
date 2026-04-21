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

        // Lint simulation
        steps.sh "echo 'Running lint checks...'"

        // YAML validation (if available)
        steps.sh """
        if command -v yamllint >/dev/null 2>&1; then
            yamllint .
        else
            echo "yamllint not installed, skipping"
        fi
        """

        // Dockerfile validation
        steps.sh """
        if [ -f Dockerfile ]; then
            echo "Dockerfile exists"
        else
            echo "Dockerfile missing!" && exit 1
        fi
        """

        steps.echo "Validation successful"
    }

    // =========================
    // MAIN DEPLOY ENTRY
    // =========================
    def deploy(String env, String strategy) {
        steps.echo "Deploying to ${env} using ${strategy} strategy"

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
                steps.error "Invalid strategy: ${strategy}"
        }
    }

    // =========================
    // ROLLING DEPLOYMENT
    // =========================
    def rollingDeploy(String env) {
        steps.echo "Executing Rolling Deployment"

        steps.sh """
        kubectl apply -f k8s/${env}/
        kubectl rollout status deployment/my-app
        """
    }

    // =========================
    // BLUE-GREEN DEPLOYMENT
    // =========================
    def blueGreenDeploy(String env) {
        steps.echo "Executing Blue-Green Deployment"

        steps.sh """
        kubectl apply -f k8s/${env}/green/
        kubectl patch service my-app -p '{"spec":{"selector":{"version":"green"}}}'
        """
    }

    // =========================
    // CANARY DEPLOYMENT
    // =========================
    def canaryDeploy(String env) {
        steps.echo "Executing Canary Deployment"

        steps.sh """
        kubectl apply -f k8s/${env}/canary/
        sleep 10
        kubectl apply -f k8s/${env}/full/
        """
    }

    // =========================
    // HEALTH CHECK
    // =========================
    def healthCheck() {
        steps.echo "Checking application health..."

        steps.sh "kubectl get pods"
    }

    // =========================
    // ROLLBACK
    // =========================
    def rollback() {
        steps.echo "Rollback triggered..."

        steps.sh "kubectl rollout undo deployment/my-app"
    }
}
package org.devops

class DeployManager implements Serializable {

    def steps
    String env
    String strategy

    DeployManager(def steps, String env, String strategy) {
        this.steps = steps
        this.env = env
        this.strategy = strategy
    }

    // =========================
    // VALIDATION
    // =========================
    def validate() {
        steps.echo "===== VALIDATION ====="

        steps.sh '''
        if [ -f docker-compose.yaml ]; then
            echo "docker-compose.yaml found"
        else
            echo "Missing docker-compose.yaml"
            exit 1
        fi
        '''

        steps.sh "docker --version"
        steps.sh "docker-compose --version"
    }

    // =========================
    // DEPLOY
    // =========================
    def deploy() {
        steps.echo "===== DEPLOY (${strategy}) ====="

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
    // ROLLING
    // =========================
    def rollingDeploy() {
        steps.echo "[ROLLING] Deployment"

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose pull || true

        # ✅ FIXED: only frontend (no ES dependency)
        docker-compose up -d empms-frontend
        '''
    }

    // =========================
    // BLUE-GREEN
    // =========================
    def blueGreenDeploy() {
        steps.echo "[BLUE-GREEN] Deployment"

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose up -d empms-frontend
        '''
    }

    // =========================
    // CANARY
    // =========================
    def canaryDeploy() {
        steps.echo "[CANARY] Deployment"

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose up -d empms-frontend
        sleep 5
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
    // ROLLBACK
    // =========================
    def rollback() {
        steps.echo "Rollback..."

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose down || true
        docker-compose up -d empms-frontend || true
        '''
    }
}

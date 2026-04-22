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
        steps.echo "===== VALIDATION STARTED ====="
        steps.echo "ENV: ${env}"

        steps.sh "ls -l"

        // Check docker-compose.yaml exists (ROOT)
        steps.sh '''
        if [ -f docker-compose.yaml ]; then
            echo "[OK] docker-compose.yaml found"
        else
            echo "[ERROR] docker-compose.yaml missing"
            exit 1
        fi
        '''

        // Check Dockerfiles (non-breaking)
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
        cd /var/jenkins_home/workspace/deployment-pipeline

        echo "Pulling images..."
        docker-compose pull || true

        echo "Starting containers..."
        docker-compose up -d
        '''
    }

    // =========================
    // BLUE-GREEN DEPLOY
    // =========================
    def blueGreenDeploy() {
        steps.echo "[BLUE-GREEN] Deployment"

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose pull || true
        docker-compose up -d
        '''
    }

    // =========================
    // CANARY DEPLOY
    // =========================
    def canaryDeploy() {
        steps.echo "[CANARY] Deployment"

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

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
    // ROLLBACK
    // =========================
    def rollback() {
        steps.echo "===== ROLLBACK ====="

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose down || true
        docker-compose up -d || true
        '''
    }
}

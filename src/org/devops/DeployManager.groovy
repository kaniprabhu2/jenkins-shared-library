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

    def deploy() {
        steps.echo "===== DEPLOY (${strategy}) ====="

        switch(strategy) {
            case "rolling":
                rolling()
                break
            case "bluegreen":
                bluegreen()
                break
            case "canary":
                canary()
                break
            default:
                steps.error "Invalid strategy"
        }
    }

    def rolling() {
        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose pull || true

        # 🔥 RUN ONLY STABLE SERVICES
        docker-compose up -d empms-frontend empms-attendance empms-employee
        '''
    }

    def bluegreen() {
        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose up -d empms-frontend
        '''
    }

    def canary() {
        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline

        docker-compose up -d empms-frontend
        sleep 5
        '''
    }

    def healthCheck() {
        steps.sh "docker ps"
    }

    def rollback() {
        steps.echo "Rollback..."

        steps.sh '''
        cd /var/jenkins_home/workspace/deployment-pipeline
        docker-compose down || true
        docker-compose up -d empms-frontend
        '''
    }
}

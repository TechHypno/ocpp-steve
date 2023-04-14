DOCKER_CFG=./src/main/resources/config/docker/main.properties
sed -i -e "/db\.password =/ s/= *.*/= $MYSQL_PASSWORD/" $DOCKER_CFG
sed -i -e "/auth\.user =/ s/= *.*/= $OCPP_ADMIN_USER/" $DOCKER_CFG
sed -i -e "/auth\.password =/ s/= *.*/= $OCPP_ADMIN_PASSWORD/" $DOCKER_CFG
sed -i -e "/webapi\.key =/ s/= *.*/= $OCPP_WEBAPI_KEY/" $DOCKER_CFG
sed -i -e "/webapi\.value =/ s/= *.*/= $OCPP_WEBAPI_VALUE/" $DOCKER_CFG
sed -i -e "/server\.host =/ s/= *.*/= $OCPP_SERVER_IP/" $DOCKER_CFG
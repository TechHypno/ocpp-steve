sed -i -e  \
"/db\.password =/ s/= *.*/= $MYSQL_PASSWORD/;"\
"/auth\.user =/ s/= *.*/= $OCPP_ADMIN_USER/;"\
"/auth\.password =/ s/= *.*/= $OCPP_ADMIN_PASSWORD/;"\
"/webapi\.key =/ s/= *.*/= $OCPP_WEBAPI_KEY/;"\
"/webapi\.value =/ s/= *.*/= $OCPP_WEBAPI_VALUE/;"\
"/server\.host =/ s/= *.*/= $OCPP_SERVER_IP/" ./src/main/resources/config/docker/main.properties
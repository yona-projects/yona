#!/bin/sh
# Yona status telegram bot shell
#
# Check Yona server status and send me/group a message with telegram
#

# Configurations start..
# ~~~~~~~~~~~~~~~~~~~~~~

# API_TOKEN=123456789:AbCdEfgijk1LmPQRSTu234v5Wx-yZA67BCD
API_TOKEN=""

# USER_CHAT_ID=123456789
USER_CHAT_ID=""

# Yona Server
# YONA_SERVER="http://my-yona-server.com"
YONA_SERVER="http://127.0.0.1:9000"

# Polling Time (sec)
POLLING_TIME=6

# Configurations end here...

####################################### 

if [ $# -eq 0 ]
  then
    if [ -z $API_TOKEN ]
        then
        echo "Telegram Bot api token is reqruied!"
        echo ""
        echo "ex>"
        echo "sh is-alive-bot.sh 328394984:AAFhL69afasfqjtUtIeRSzIagVYw7H3zF4"
        exit 1
    fi
else
    API_TOKEN=$1
fi

if [ -z $USER_CHAT_ID ]
  then
    if [ -z $2 ]
        then
        echo "---------------------------------------------------"
        echo "Find chat id of user or group with your own eyes.. "
        echo "---------------------------------------------------"
        echo ""
        curl -sL https://api.telegram.org/bot$API_TOKEN/getUpdates
        echo ""
        echo "---------------------------------------------------"
        echo ""
        echo "and then retry..."
        echo "sh is-alive-bot.sh API_TOKEN USER_CHAT_ID"
        echo ""
        echo "ex>"
        echo "sh is-alive-bot.sh 328394984:AAFhL69afasfqjtUtIeRSzIagVYw7H3zF4 2156789"
        exit 1;
        else
        USER_CHAT_ID=$2
    fi
fi

### preparing for message and server status check

message=""
messageOnDown="Yona is unavaliable!"
messageOnRevive="Yona comeback!"

sendMessageToTelegram() {
  curl -s \
    -X POST \
    https://api.telegram.org/bot$API_TOKEN/sendMessage \
    -d text="$message" \
    -d chat_id=$USER_CHAT_ID > /dev/null
}

# it is used for one time message sending
isDown=false

echo ""
echo "Monitoring started... $(date +%Y-%m-%d" "%H:%M:%S)"
echo ""
while true
do
    NOW=$(date +%Y-%m-%d" "%H:%M:%S)
    curl -sL -H "Accept: application/json" $YONA_SERVER/-_-api/v1/hello | grep "\"ok\":true" > /dev/null
    if [ $? != 0 ]
        then
            if [ $isDown == false ] 
                then
                    echo "Sending message - $messageOnDown - $NOW" 
                    isDown=true
                    message="$messageOnDown - $YONA_SERVER"
                    sendMessageToTelegram
            fi
        else
            if [ $isDown == true ] 
                then
                echo "Sending message - $messageOnRevive - $NOW" 
                isDown=false
                message="$messageOnRevive - $YONA_SERVER"
                sendMessageToTelegram
            fi
    fi
    sleep $POLLING_TIME
done;

#
# referred pages
#
# https://core.telegram.org/bots#3-how-do-i-create-a-bot
# https://core.telegram.org/bots/api
# https://community.onion.io/topic/499/sending-telegram-messages-via-bots
#

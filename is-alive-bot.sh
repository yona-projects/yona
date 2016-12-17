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

# TARGET_SERVER="http://my-yona-server.com"
TARGET_SERVER="http://127.0.0.1:9000"

CHECK_URL="$TARGET_SERVER/-_-api/v1/hello"

EXPECTED_STATUS_CODE=200

# Polling Time (sec)
POLLING_TIME=60

# Messages
messageOnDown="Yona is unavaliable!"
messageOnRevive="Yona comeback!"


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
        echo ""
        echo "See: https://core.telegram.org/bots#3-how-do-i-create-a-bot"
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
        echo ""
        echo "..chat\":{ \"id\": 621884 ...  <= user or group id!"
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

sendMessageToTelegram() {
  curl -s \
    -X POST \
    https://api.telegram.org/bot$API_TOKEN/sendMessage \
    -d text="$1" \
    -d chat_id=$USER_CHAT_ID > /dev/null
}

# it is used for one time message sending
isDown=false

echo ""
echo "Monitoring started... $(date +%Y-%m-%d" "%H:%M:%S)"
echo ""

sendMessageToTelegram "Monitoring started... - $TARGET_SERVER"

while true
do
    NOW=$(date +%Y-%m-%d" "%H:%M:%S)
    response=$(curl -I $CHECK_URL 2> /dev/null | head -n 1 | cut -d$' ' -f2)
    if [ -z $response ] || [ $response != $EXPECTED_STATUS_CODE ]
        then
            if [ $isDown == false ] 
                then
                    echo "Sending message - $messageOnDown - $NOW" 
                    isDown=true
                    sendMessageToTelegram "$messageOnDown $response - $TARGET_SERVER"
            fi
        else
            if [ $isDown == true ] 
                then
                echo "Sending message - $messageOnRevive - $NOW" 
                isDown=false
                sendMessageToTelegram "$messageOnRevive - $TARGET_SERVER"
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

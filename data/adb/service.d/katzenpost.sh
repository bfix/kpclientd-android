#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done
/data/adb/modules/katzenpost/kpclientd -c /data/adb/modules/katzenpost/client_ws.toml > /data/local/tmp/kpclient.log 2>&1 &

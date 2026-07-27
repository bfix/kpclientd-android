#!/system/bin/sh

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done
sleep 2

cd /data/adb/modules/kpclientd/
if [ ! -e "disabled" ]; then
    rm /data/local/tmp/kpclientd.log
    ./kpclientd -c client_ws.toml > /data/local/tmp/kpclientd.log 2>&1 &
fi
exit 0

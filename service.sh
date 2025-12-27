#!/system/bin/sh

PACKAGE_NAME="com.final_pj.voice"

cmd telecom set-default-dialer $PACKAGE_NAME

log -p i -t Magisk "Set $PACKAGE_NAME as default Dialer"
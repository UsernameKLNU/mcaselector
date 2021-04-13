#!/bin/bash

rm -fr build/tmp/winrun4j
mkdir -p build/tmp/winrun4j

wget https://github.com/poidasmith/winrun4j/files/1822558/winrun4J-0.4.5.zip -O build/tmp/winrun4j/winrun4j.zip
unzip build/tmp/winrun4j/winrun4j.zip -d build/tmp/winrun4j/
rm build/tmp/winrun4j/winrun4j.zip

wget http://www.angusj.com/resourcehacker/resource_hacker.zip -O build/tmp/winrun4j/resourcehacker.zip
unzip build/tmp/winrun4j/resourcehacker.zip -d build/tmp/winrun4j/
rm build/tmp/winrun4j/resourcehacker.zip

mkdir -p ~/".wine/drive_c/winrun4j"
cp build/tmp/winrun4j/winrun4j/bin/WinRun4J64.exe ~/".wine/drive_c/winrun4j"
cp build/tmp/winrun4j/winrun4j/bin/RCEDIT64.exe ~/".wine/drive_c/winrun4j"
cp build/tmp/winrun4j/resource_hacker/ResourceHacker.exe ~/".wine/drive_c/winrun4j"
cp inno/icon.ico ~/".wine/drive_c/winrun4j"
cp travis/winrun4j/winrun4j.ini ~/".wine/drive_c/winrun4j"
cp travis/winrun4j/resource.rc ~/".wine/drive_c/winrun4j"

echo "assembling winrun4j"
unset DISPLAY
exePath=$(winepath -w "build/tmp/winrun4j/winrun4j/bin/WinRun4J64.exe")
wine "C:\winrun4j\RCEDIT64.exe" "/C" "$exePath"
wine "C:\winrun4j\RCEDIT64.exe" "/I" "$exePath" "$(winepath -w "inno/icon.ico")"
wine "C:\winrun4j\RCEDIT64.exe" "/N" "$exePath" "$(winepath -w "travis/winrun4j/winrun4j.ini")"
wine "C:\winrun4j\ResourceHacker.exe" "-open C:\winrun4j\resource.rc -save C:\winrun4j\resource.rec -action compile"
wine "C:\winrun4j\ResourceHacker.exe" "-open $exePath -save $exePath -action add -resource C:\winrun4j\resource.rec"

mkdir -p build/winrun4j
cp build/tmp/winrun4j/winrun4j/bin/WinRun4J64.exe "build/winrun4j/MCA Selector.exe"

echo "----------------------------------------------"
pwd
ls -lah build/tmp/winrun4j
ls -lah build/tmp/winrun4j/winrun4j/bin
ls -lah build/winrun4j
echo "----------------------------------------------"
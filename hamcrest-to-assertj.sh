#!/usr/bin/env bash
files=$(grep -lR assertEquals . | grep DataSetUtilsTest.java$)

for file in $files
do
#function replace(){
# echo "Replacing ${1} with ${2}"
echo "Processing file: $file"
# perl -0777 -i.original -pe 's/"${1}"/${2}/g' "$file"
  perl -0777 -i.original -pe 's/assertEquals\(("?\w*\s\w*")\,\s("\w*")\,\s*(\w*.*)\)/assertThat(\3).as(\1).isEqualTo(\2)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((.*)\,\n?(.*)\.length\,\n?(.*)\.length\)/assertThat(\3).as(\1).hasSameSizeAs(\2)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((.*)\,\s(DataSetUtils.getEscapedName\(.*\))\)/assertThat(\2).isEqualTo(\1)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((".*"),\s((".*")),\s*(.*)\)/assertThat(\3).as(\1).isEqualTo(\2)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\(("\[?[\w]*\]?"),\s*(D.*)\)/assertThat(\3).as(\1).isEqualTo(\2)/g' $file  
  perl -0777 -i.original -pe 's/assertEquals\(("\\"?\w*\\"?"),(.*)\)/assertThat(\2).isEqualTo(\1)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((.*)\,\n?(\s*null.*)\,\n?(.*)\)/assertThat(\3).as(\1).isNull()/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((".*")\,\s(false)\,\s(.*)\)/assertThat(\3).as(\1).isFalse()/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((".*"),(\strue), (.*)\)/assertThat(\3).as(\1).isTrue()/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((".*")\,\s(.*)\,\s(.*)\)/assertThat(\3).as(\1).isEqualTo(\2)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((["\w\s\w"+\w]*)\,\s?\n?[\s]*(0)\,\n?\,?[\s]*(.*)\)/assertThat(\3).as(\1).isZero()/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((["\w\s\w"+\w]*)\,\s?\n?[\s]*(.*)\,\n?\,?[\s]*(.*)\)/assertThat(\3).as(\1).isEqualTo(\2)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((.*)\,\n?(.*)\,\n?(.*)\)/assertThat(\3).as(\1).isEqualTo(\2)/g' $file
  perl -0777 -i.original -pe 's/assertEquals\((.*)\,(.*)\)/assertThat(\2).isEqualTo(\1)/g' $file
  perl -0777 -i.original -pe 's/assertTrue\((\S*\s*\S*\s*\S*\s[a-zA-Z]*)\,(.*)(.*\<)(.*\d?)\)/assertThat(\2).as(\1).isNegative()/g' $file
  perl -0777 -i.original -pe 's/assertTrue\((\S*\s*\S*\s*\S*\s[a-zA-Z]*)\,[\s*]*(.*)\>(.*\d?)\)/assertThat(\2).as(\1).isPositive()/g' $file
  perl -0777 -i.original -pe 's/assertNotNull\((.*)\)/assertThat($1).isNotNull()/g' $file
#}
done

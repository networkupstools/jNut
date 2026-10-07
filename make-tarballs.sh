#!/bin/sh

# Helper to create tarballs for NUT-Website publication
set -e
set -u

POM_VERSION="`mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout`"
echo "${POM_VERSION}"

rm -rf tmp/tarballs
mkdir -p tmp/tarballs/jNut-"${POM_VERSION}" tmp/tarballs/jNutWebAPI-"${POM_VERSION}"

MVN_ARGS="-B"
if [ x"$1" = x-f ] ; then
    mvn $MVN_ARGS clean
    MVN_ARGS="$MVN_ARGS -U"
fi

mvn $MVN_ARGS package || exit

# Local dependencies for javadocs stage:
mvn $MVN_ARGS install || exit
mvn $MVN_ARGS javadoc:javadoc || exit

# Both bundles need the parent POM and all modules to rebuild from source:
tar cz --exclude target \
	-f tmp/tarballs/jNut-"${POM_VERSION}"/jNut-"${POM_VERSION}"-src.tar.gz \
	pom.xml LICENSE.md README.adoc make-tarballs.sh test-NIT.sh \
	jNut jNutList jNutWebAPI
cp tmp/tarballs/jNut-"${POM_VERSION}"/jNut-"${POM_VERSION}"-src.tar.gz \
	tmp/tarballs/jNutWebAPI-"${POM_VERSION}"/

# Library and sample client go together:
cp LICENSE.md README.adoc test-NIT.sh tmp/tarballs/jNut-"${POM_VERSION}"/
cp jNut/target/*.jar jNutList/target/*.jar tmp/tarballs/jNut-"${POM_VERSION}"/
cp -r jNut/target/reports/apidocs/     tmp/tarballs/jNut-"${POM_VERSION}"/jNut-apidocs/
cp -r jNutList/target/reports/apidocs/ tmp/tarballs/jNut-"${POM_VERSION}"/jNutList-apidocs/

# REST API is larger and standalone (as an artifact):
cp jNutWebAPI/target/*.war tmp/tarballs/jNutWebAPI-"${POM_VERSION}"/
cp -r jNutWebAPI/target/reports/apidocs/ tmp/tarballs/jNutWebAPI-"${POM_VERSION}"/jNutWebAPI-apidocs/

(cd tmp/tarballs/ && tar czf - jNut-"${POM_VERSION}") > jNut-"${POM_VERSION}".tar.gz
(cd tmp/tarballs/ && tar czf - jNutWebAPI-"${POM_VERSION}") > jNutWebAPI-"${POM_VERSION}".tar.gz

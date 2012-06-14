rm -rf bin
rm -rf dist
rm -rf target
find samples-and-tests -name tmp | xargs rm -rf
find samples-and-tests -name precompiled | xargs rm -rf
find samples-and-tests -name modules | xargs rm -rf
play build-module
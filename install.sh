#!/usr/bin/env bash

function parse_yaml {
   local prefix=$2
   local s='[[:space:]]*' w='[a-zA-Z0-9_]*' fs=$(echo @|tr @ '\034')
   sed -ne "s|^\($s\):|\1|" \
        -e "s|^\($s\)\($w\)$s:$s[\"']\(.*\)[\"']$s\$|\1$fs\2$fs\3|p" \
        -e "s|^\($s\)\($w\)$s:$s\(.*\)$s\$|\1$fs\2$fs\3|p"  $1 |
   awk -F$fs '{
      indent = length($1)/2;
      vname[indent] = $2;
      for (i in vname) {if (i > indent) {delete vname[i]}}
      if (length($3) > 0) {
         vn=""; for (i=0; i<indent; i++) {vn=(vn)(vname[i])("_")}
         printf("%s%s%s=\"%s\"\n", "'$prefix'",vn, $2, $3);
      }
   }'
}

BASEDIR=$(dirname "$0")
eval $(parse_yaml $BASEDIR/src/boost/openshift/definitions/product.yml)
rm -rf $BASEDIR/src/boost/openshift/playbooks
mkdir -p $BASEDIR/src/boost/openshift/playbooks 
wget https://raw.githubusercontent.com/boostcd/boostcd/$boost_version/src/boost/openshift/playbooks/install.yml -q -P $BASEDIR/src/boost/openshift/playbooks 
wget https://raw.githubusercontent.com/boostcd/boostcd/$boost_version/src/boost/openshift/playbooks/hosts.ini -q -P $BASEDIR/src/boost/openshift/playbooks 
ansible-playbook -i $BASEDIR/src/boost/openshift/playbooks/hosts.ini $BASEDIR/src/boost/openshift/playbooks/install.yml



.PHONY: list
list:
	@$(MAKE) -pRrq -f $(lastword $(MAKEFILE_LIST)) : 2>/dev/null | awk -v RS= -F: '/^# File/,/^# Finished Make data base/ {if ($$1 !~ "^[#.]") {print $$1}}' | sort | egrep -v -e '^[^[:alnum:]]' -e '^$@$$'

# Install Boost Core Locally
install:
	@./install.sh

# Installs Boost Development Environment
install_boost_development:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/install_boost_development.yml

# Uninstalls Boost Development Environment
uninstall_boost_development:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/uninstall_boost_development.yml

# Installs the Product Environment
install_product:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/install_product.yml

# Uninstalls the Product Environment
uninstall_product:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/uninstall_product.yml

# Installs the Boost Services
install_services:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/install_services.yml

# Uninstalls the Boost Services
uninstall_services:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/uninstall_services.yml	

# Update Environments
update_environments:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/update_environments.yml

# Update Microservices
update_microservices:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/update_microservices.yml

# Update Libraries
update_libraries:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/update_libraries.yml

# Update Openshift
update_openshift:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/update_openshift.yml	

# Update Product
update_product:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/update_product.yml	

# Update Users
update_users:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/update_users.yml

# Update Boost and Services to the Stated Version
update:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/update.yml

# Repair Product Jenkins
repair_product_jenkins:
	@ansible-playbook -i src/boost/openshift/playbooks/hosts.ini src/boost/openshift/playbooks/repair_product_jenkins.yml
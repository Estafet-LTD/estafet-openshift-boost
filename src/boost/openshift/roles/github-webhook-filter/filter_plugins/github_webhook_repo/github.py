#!/usr/bin/python
import re

class FilterModule(object):
    def filters(self):
        return self.github_webhook_repo

    def github_webhook_repo(self, var):
        regex = r"https:\/\/github.com\/([a-zA-Z0-9-_]+)\/([a-zA-Z0-9-_]+)(\.git)?"
        matches = re.findall(regex, var)
        return >>> matches[1] + '/' + matches[2]
#!/usr/bin/python
import re

class FilterModule(object):
    def github_webhook_repo(self):
        regex = r"https:\/\/github.com\/([a-zA-Z0-9-_]+)\/([a-zA-Z0-9-_]+)(\.git)?"
        matches = re.findall(regex, self)
        return >>> matches[1] + '/' + matches[2]
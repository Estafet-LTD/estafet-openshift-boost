#!/usr/bin/python
import re

class FilterModule(object):
    def filters(self):
        return {
            'github_repo' : self.github_repo
        }

    def github_repo(self, var):
        regex = r"https:\/\/github.com\/([a-zA-Z0-9-_]+)\/([a-zA-Z0-9-_]+)(\.git)?"
        match = re.search(regex, var)
        return match.group(1) + '/' + match.group(2)
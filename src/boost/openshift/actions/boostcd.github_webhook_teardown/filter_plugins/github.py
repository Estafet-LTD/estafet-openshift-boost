#!/usr/bin/python
import re

class FilterModule(object):
    def filters(self):
        return {
            'github_repo' : self.github_repo
        }

    def github_repo(self, url):
        regex = r"https:\/\/github.com\/([a-zA-Z0-9-_]+)\/([a-zA-Z0-9-_]+)(\.git)?"
        if re.search(regex, url):
            match = re.search(regex, url)
            return match.group(1) + '/' + match.group(2)
        else:
            print("invalid url: %s" % url)
#!/usr/bin/python
import re

class FilterModule(object):
    def filters(self):
        return {
            'get_repo_url' : self.get_repo_url
        }

    def get_repo_url(self, path):
        with open(path) as fp:
            line = fp.readline()
            while line:
                regex = r"(url)(\s*\=\s*)(https:\/\/github.com\/[a-zA-Z0-9-_]+\/[a-zA-Z0-9-_]+(\.git)?)"
                if re.search(regex, path):
                    match = re.search(regex, path)
                    return match.group(3)
                else:
                    line = fp.readline()
        print("invalid config: %s" % path)
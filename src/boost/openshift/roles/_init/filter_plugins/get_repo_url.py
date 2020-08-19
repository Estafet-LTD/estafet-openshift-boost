#!/usr/bin/python
import ConfigParser

class FilterModule(object):
    def filters(self):
        return {
            'get_repo_url' : self.get_repo_url
        }

    def get_repo_url(self, path):
        config = ConfigParser.ConfigParser()
        config.read(path)
        return config['remote "origin"']['url']
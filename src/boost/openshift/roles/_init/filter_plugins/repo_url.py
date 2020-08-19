#!/usr/bin/python
import configparser

class FilterModule(object):
    def filters(self):
        return {
            'get_repo_url' : self.get_repo_url
        }

    def get_repo_url(self, path):
        config = configparser.ConfigParser()
        config.read(path)
        return config['remote "origin"']['url']
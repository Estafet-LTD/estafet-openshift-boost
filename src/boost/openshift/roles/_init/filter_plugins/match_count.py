#!/usr/bin/python
import json

class FilterModule(object):
    def filters(self):
        return {
            'match_count' : self.match_count
        }

    def match_count(self, stdout, match):
        print stdout
        print match
        pods = json.loads(stdout)
        items = pods["items"]
        count = 0
        for item in items:
            if item["metadata"]["name"].startswith(match):
                count++
        return count
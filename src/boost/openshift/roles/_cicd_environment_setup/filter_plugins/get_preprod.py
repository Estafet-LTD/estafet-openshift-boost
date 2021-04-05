#!/usr/bin/python

class FilterModule(object):
    def filters(self):
        return {
            'get_preprod' : self.get_preprod
        }

    def get_preprod(self, stages):
        for i in range(len(stages)):      
            if stages[i]["name"] == "prod" :
                return stages[i-1]["name"]
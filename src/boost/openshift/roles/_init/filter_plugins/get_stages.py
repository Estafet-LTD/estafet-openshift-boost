#!/usr/bin/python

class FilterModule(object):
    def filters(self):
        return {
            'get_preprod' : self.get_preprod,
            'get_mid_stages' : self.get_mid_stages
        }

    def get_preprod(self, stages):
        for i in range(len(stages)):      
            if stages[i]["name"] == "prod" :
                return stages[i-1]["name"]

    def get_mid_stages(self, stages):
        preprod = get_preprod(stages)
        mid_stages = []
        for stage in stages:
            if stage["name"] not in ['build', preprod, 'prod']:
                mid_stages.append(stage["name"])
        return mid_stages

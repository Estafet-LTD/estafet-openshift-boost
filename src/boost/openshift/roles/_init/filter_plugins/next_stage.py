#!/usr/bin/python

class FilterModule(object):
    def filters(self):
        return {
            'next_stage' : self.next_stage
        }

    def next_stage(self, stage, stages):
        if stage == "build":
            return "test"
        elif stage == "prod":
            return "end"        
        for i in range(len(stages)):      
            if stage == stages[i]["name"]:
                return stages[i+1]["name"]
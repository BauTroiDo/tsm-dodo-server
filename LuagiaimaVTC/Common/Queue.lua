--region Queue.lua
--[[
     無限增加的佇列
]]
Queue = {};
local this = Queue; 
this.__index = Queue;

function Queue.New()
  local queue={};
  setmetatable(queue, Queue);
  queue.data = {};
  queue.Count = 0;
  return queue;
end

function Queue:Enqueue(value)
  table.insert(self.data,value)
  self.Count = self.Count + 1;
end

function Queue:Dequeue()
  if self.Count ~= 0 then 
    local value = self.data[1];
    table.remove(self.data, 1);
    self.Count = self.Count-1;
    return value;
  else
    return nil;
  end
end
--endregion

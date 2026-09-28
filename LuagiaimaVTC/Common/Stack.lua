
Stack = {}
Stack.__index = Stack

function Stack.New()
  local self = {};
  setmetatable(self, Stack);
  
  self.values = {};
  
  return self;
end

function Stack.Push(self, value)
  table.insert(self.values, value);
end

function Stack.Peek(self)
  return self.values[table.maxn(self.values)];
end

function Stack.Pop(self)
  local value = self.values[table.maxn(self.values)];
  
  table.remove(self.values, table.maxn(self.values));
  
  return value;
end

function Stack.Count(self)
  return table.maxn(self.values);
end

function Stack.Clear(self)
  table.Clear(self.values);
end
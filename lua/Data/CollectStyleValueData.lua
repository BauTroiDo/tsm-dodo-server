CollectStyleValueData = {}
CollectStyleValueData.__index = CollectStyleValueData;

function CollectStyleValueData.New(reader)
  local self = {};
  setmetatable(self, CollectStyleValueData);
  self.score = reader:ReadUInt16();
  self.attribute = {};
  for i = 1, 4 do 
    self.attribute[i] = {};    
    self.attribute[i].kind = reader:ReadByte();
    self.attribute[i].value = reader:ReadUInt16();
  end    

  return self;
end

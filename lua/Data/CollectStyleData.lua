CollectStyleData = {}
CollectStyleData.__index = CollectStyleData;

function CollectStyleData.New(reader)
  local self = {};
  setmetatable(self, CollectStyleData);
  self.id = reader:ReadUInt16();
  self.name  =  reader:ReadUInt32();
  self.itemId = {};
  for i = 1, 5 do --頭身武手腳
    self.itemId[i] = reader:ReadUInt16();
  end
  self.info = reader:ReadUInt32();

  self.itemScore = {};
  for i = 1, 6 do 
    self.itemScore[i] = reader:ReadUInt16();
  end

  return self;
end

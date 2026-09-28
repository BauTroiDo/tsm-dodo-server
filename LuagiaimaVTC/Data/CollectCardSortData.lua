
CollectCardSortData = {}
CollectCardSortData.__index = CollectCardSortData;

function CollectCardSortData.New(reader)
  local self = {};
  self.id = reader:ReadUInt16();
  self.warriorId = reader:ReadUInt16();  
  self.roleCount = reader:ReadUInt16();
  self.getFlag = reader:ReadUInt16();
  return self;
end

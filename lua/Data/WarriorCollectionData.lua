WarriorCollectionData = {}
WarriorCollectionData.__index = WarriorCollectionData;

function WarriorCollectionData.New(reader)
  local self = {};
  setmetatable(self, WarriorCollectionData);
  self.roleId = {};
  for i = 1, 5 do 
    self.roleId[i] = reader:ReadUInt16();  
  end

  self.info = reader:ReadUInt32();
  return self;
end
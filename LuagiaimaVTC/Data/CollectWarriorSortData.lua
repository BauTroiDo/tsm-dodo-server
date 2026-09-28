CollectWarriorSortData = {}
CollectWarriorSortData.__index = CollectWarriorSortData;

function CollectWarriorSortData.New(reader)
  local self = {};
  setmetatable(self, CollectWarriorSortData);
  self.index = reader:ReadUInt16();   
  self.ids = {};
  for i = 1, 3 do 
    self.ids[i] = reader:ReadUInt16();
  end
  return self;
end
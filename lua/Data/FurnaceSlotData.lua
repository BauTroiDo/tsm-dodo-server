FurnaceSlotData = {};
FurnaceSlotData.__index = FurnaceSlotData;

function FurnaceSlotData.New(reader)
  local self = {};
  setmetatable(self, FurnaceSlotData);

  self.Id = reader:ReadUInt16();
  self.kind = reader:ReadByte();
  self.awardKind = reader:ReadByte();
  self.itemId = reader:ReadUInt16();
  self.bOpen = reader:ReadByte();
  self.OpenYear = reader:ReadUInt16();
  self.OpenMon = reader:ReadByte();
  self.OpenDay = reader:ReadByte();
  self.CloseYear = reader:ReadUInt16();
  self.CloseMon = reader:ReadByte();
  self.CloseDay = reader:ReadByte();

  return self;
end

function FurnaceSlotData.IsInTime(data)
  local startTime = DateTime.New( data.OpenYear, data.OpenMon, data.OpenDay, 0, 0, 0 );
  local endTime = DateTime.New( data.CloseYear, data.CloseMon, data.CloseDay, 23, 59, 59 );
  local diffEnd = DateTime.Compare(endTime, CGTimer.serverTime);
  local diffStart = DateTime.Compare(CGTimer.serverTime, startTime);

  if diffEnd > 0 and diffStart > 0 then
    return true;
  else
    return false;
  end
end
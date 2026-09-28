BattleRoyaleScheduleData= {}
BattleRoyaleScheduleData.__index = BattleRoyaleScheduleData;
local this = BattleRoyaleScheduleData;

function BattleRoyaleScheduleData.New(reader)
    local self = {};
    setmetatable(self, BattleRoyaleScheduleData);
    self.time = reader:ReadUInt16();
    self.textId = reader:ReadInt32();
    return self;
end

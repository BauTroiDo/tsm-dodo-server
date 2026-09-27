CharmingBodyMe = InheritsFromBaseStatus();

function CharmingBodyMe:Update()
  local me = FightField.fightHum[self.humIdx];
  local status = me:GetStatus(self.statusKind);
  for k,v in pairs(me.fightBuff) do
    if v.skillId == 42002 and v.statusID == 59 and self.step == 1 then
      status:SetLightWithOrder(0, EEffectLightKind.Sky, 20059, 100, 1, 12, 12, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y - 10, 30, true, 45);
      self.step = 2;
    end
  end
end

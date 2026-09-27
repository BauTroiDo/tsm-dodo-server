EmperorAuraMe = InheritsFromBaseStatus();

function EmperorAuraMe:Update()
  local me = FightField.fightHum[self.humIdx];
  local status = me:GetStatus(self.statusKind);
  for k,v in pairs(me.fightBuff) do
    if v.skillId == 42001 and v.statusID == 60 and self.step == 1 then
      status:SetLightWithOrder(0, EEffectLightKind.Sky, 20013, 100, 1, 12, 12, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y - 10, 30, true, 45);
      self.step = 2;
    end
  end
end

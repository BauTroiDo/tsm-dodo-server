TardyMe = InheritsFromBaseStatus();

function TardyMe:ExtraClose()  
  FightField.RestoreFightInfo(self.humIdx, 0);
  FightField.fightHum[self.humIdx].roleController:SetTextureColor(Color.White);
end

function TardyMe:Update()
  local me = FightField.fightHum[self.humIdx];
  if self.step == 1 then 
    me.status[self.statusKind]:SetLightWithOrder(1, EEffectLightDis.Ground, 10259, 150, 4, 10, 10, EEffectLightTracer.Stand, me.nowPos.x , me.nowPos.y + 45, 255, true, 25);      
    me.roleController:SetTextureColor(Color.Orange);
    self.step = 2;
   elseif self.step == 2 then

  end
end
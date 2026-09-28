ExitFail = InheritsFromBaseAttackSkill();

function ExitFail:Update()
  local me = FightField.fightHum[self.humIdx];
  local newPos;
  if self.step == 1 then
    newPos = me:GetSpotOfRc(EFightSpot.Back, 30);
    me.roleController:SetInBattle(false);
    me.roleController:SetIsMoving(true);
    me:SetSpeed(0.12);
    me:SetWalk(newPos.x, newPos.y);
    self.step = self.step + 1;
  elseif self.step == 2 then
    if me.arrive == true then
      newPos = me:GetSpotOfRc(EFightSpot.Back, 60);
      me.arrive = false;
      me.roleController:SetIsMoving(false);
      me.roleController:SetInBattle(true);
      me.roleController:SetIsDead(true);
      me:SetSpeed(0.12);
      me:SetWalk(newPos.x, newPos.y);
      self.interval = 0.8;
      self.step = self.step + 1;      
    end
  elseif self.step == 3 then
    if me.arrive == true then
      if self:CheckInterval() then 
        self.step = self.step + 1;
      end
    end
  elseif self.step == 4 then
    self.isLightEnd = true;
  end
end
OutEnemy = InheritsFromBaseAttackSkill();

function OutEnemy:Update()
  local me = FightField.fightHum[self.humIdx];
  
  if self.step == 1 then
    local newPos = me:GetMagicPos();
    me:SetMoment(newPos.x, newPos.y, false);
    me.roleController:MagicAttack();
    self.interval = 1;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      self.isLightEnd = true;
      self.step = 3;
    end
  end
end
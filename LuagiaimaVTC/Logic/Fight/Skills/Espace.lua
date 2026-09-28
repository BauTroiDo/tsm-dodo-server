Espace = InheritsFromBaseAttackSkill();

function Espace:Update()
  local newPos;
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then             
    newPos = target:GetSpot(EFightSpot.Back, 60);
    target:SetWalk(newPos.x, newPos.y);
    target.roleController:SetIsJumping(true);
    target:SetSpeed(0.12);
    target:SetBeh(EFightBeh.Residual);        
    self.step = self.step + 1;      
  elseif self.step == 2 then 
    if target.arrive then                
      target.roleController:SetVisible(ERoleVisible.Hide);  --隱藏
      self.step = self.step + 1;      
      self.isLightEnd = true; --特技施展完畢
    end
  end
end
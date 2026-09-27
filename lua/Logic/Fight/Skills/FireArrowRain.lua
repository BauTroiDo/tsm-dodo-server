BurnMe = InheritsFromBaseStatus();

local CONST_OFFSET_Y = -40;

function BurnMe:Update()
  local me = FightField.fightHum[self.humIdx];
  local newPos = me:RCtoSpot(me.colm, me.row);
  newPos.y = newPos.y + CONST_OFFSET_Y;

  if self.step == 1 then
    me.status[self.statusKind]:SetLightWithOrder(1,EEffectLightKind.Status, 10514, 100, 1, 4, 4, 
      EEffectLightTracer.Stand, newPos.x, newPos.y , 80, true, 80);
    self.step = 2;
  end
end

package furgl.infinitory.impl.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public interface IPlayerInventory {

	/**Get additional slots for this player - always multiple of 9*/
	public int getAdditionalSlots();
	
	public void setAdditionalSlots(int additionalSlots);

	/**Mark as needing to update additional slots of infinitory*/
	public void needToUpdateInfinitorySize();
		
	/**Mark as needing to sort inventory*/
	public void needToSort();

	/**Mark as needing to update client*/
	void needToUpdateClient();
	
	public SortingType getSortingType();

	/**Recalculate additional slots based on main and infinitory sizes / fullness*/
	void updateInfinitorySize();
	
}
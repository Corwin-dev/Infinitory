package furgl.infinitory.mixin.inventory;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import furgl.infinitory.config.Config;
import furgl.infinitory.impl.inventory.IPlayerInventory;
import furgl.infinitory.impl.inventory.IScreenHandler;
import furgl.infinitory.impl.inventory.SortingType;
import furgl.infinitory.impl.lists.MainDefaultedList;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin implements Inventory, IPlayerInventory {

	// Separate main and infinitory
	// PROS: easy to read/write, 
	// CONS: buggy af with server->client sync

	// Expanding main
	// PROS: 
	// CONS: harder to read/write, slot ids need to change when resizing?

	@Unique
	private SortingType sortingType;
	@Unique
	private int additionalSlots;
	@Unique
	private boolean needToUpdateInfinitorySize;
	@Unique
	private int needToUpdateClient;
	@Unique
	private boolean needToSort;

	@Shadow @Final @Mutable
	public DefaultedList<ItemStack> main;
	@Shadow @Final
	public DefaultedList<ItemStack> armor;
	@Shadow @Final
	public DefaultedList<ItemStack> offHand;
	@Shadow @Final @Mutable
	private List<DefaultedList<ItemStack>> combinedInventory;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void constructor(CallbackInfo ci) {
		this.main = MainDefaultedList.ofSize(36, ItemStack.EMPTY, this);
		this.combinedInventory = ImmutableList.of(this.main, this.armor, this.offHand);
		this.updateInfinitorySize();
	}

	// ========== SORTING ==========

	@Unique
	private void sort() {
		/*this.sortingType = SortingType.QUANTITY; // TODO remove
		if (!((PlayerInventory)(Object)this).player.world.isClient && this.getSortingType() != SortingType.NONE) {
			// combine all items into one list
			this.mainInfinitory.clear();
			for (List<ItemStack> list : Lists.newArrayList(this.infinitory, this.main.subList(9, this.main.size())))
				for (ItemStack addingStack : list) {
					outer:
						if (!addingStack.isEmpty()) {
							// try to stack with existing items in list
							for (ItemStack stack : this.mainInfinitory)
								if (this.canStackAddMore(stack, addingStack)) {
									int amountToAdd = addingStack.getCount();
									if (amountToAdd > this.getMaxCountPerStack() - stack.getCount()) 
										amountToAdd = this.getMaxCountPerStack() - stack.getCount();
									if (amountToAdd > 0) {
										System.out.println("combining "+amountToAdd+"x "+addingStack.getName().getString()); // TODO remove
										stack.increment(amountToAdd);
										addingStack.decrement(amountToAdd);
										// addingStack empty - we can skip to the next item
										if (addingStack.isEmpty())
											break outer;
									}
								}
							// didn't find anything to stack with, add to list (don't worry about max size - should be handled already)
							System.out.println("adding "+addingStack.getCount()+"x "+addingStack.getName().getString()); // TODO remove
							this.mainInfinitory.add(addingStack.copy());
						}
				}
			// sort
			this.getSortingType().sort(this.mainInfinitory, true);
			// copy from sorted list to main and infinitory
			for (int i=9; i<this.main.size(); ++i)
				this.main.set(i, ItemStack.EMPTY);
			this.infinitory.clear();
			for (int i=0; i<this.mainInfinitory.size(); ++i) 
				if ((i+9)<this.main.size())
					this.main.set(i+9, this.mainInfinitory.get(i));
				else
					this.infinitory.add(this.mainInfinitory.get(i));
			// update to client
			this.needToUpdateClient = true;
		}*/
	}

	@Unique
	@Override
	public SortingType getSortingType() {
		if (this.sortingType == null)
			this.sortingType = SortingType.NONE;
		return this.sortingType;
	}

	@Unique
	@Override
	public void needToSort() {
		this.needToSort = true;
	}

	// ========== EXPANDING INVENTORY ==========

	/**Update main and infinitory full/empty status*/
	@Unique
	public void updateFullEmptyStatus() {
		/*for (ListeningDefaultedList list : new ListeningDefaultedList[] {(ListeningDefaultedList) this.main, this.infinitory}) {
			list.isEmpty = true;
			list.isFull = true;
			for (int i=list instanceof MainDefaultedList ? 9 : 0; i<list.size(); ++i) {
				ItemStack stack = list.get(i);
				if (stack.isEmpty())
					list.isFull = false;
				else 
					list.isEmpty = false;
			}*/
		//System.out.println(list.getClass()+", isEmpty: "+list.isEmpty+", isFull: "+list.isFull+", "+list); // TODO remove
		this.updateInfinitorySize();
		//}

	}

	/**Recalculate additional slots based on main and infinitory sizes / fullness*/
	@Unique
	@Override
	public void updateInfinitorySize() { 
		//System.out.println("main: "+this.main.size()+this.main); // TODO remove
		// get indexes of first and last items
		boolean isFull = true;
		boolean isFullExceptLastRow = true;
		boolean lastRowEmpty = true;
		int lastItem = -1;
		for (int i = this.main.size()-1; i>=9; --i) { // last 5 of main are armor and offhand
			boolean empty = this.main.get(i).isEmpty();
			if (!empty && lastItem == -1) 
				lastItem = i;
			if (empty)
				isFull = false;
			if (i >= this.main.size()-9) {
				if (!empty)
					lastRowEmpty = false;
			}
			else if (empty)
				isFullExceptLastRow = false;
			//System.out.println("checking: i: "+i+", empty: "+empty+", stack: "+this.main.get(i)); // TODO remove
		}
		// index of last item rounded up to multiple of 9 additional slots
		this.setAdditionalSlots(lastItem - 36 + (isFull || (isFullExceptLastRow && lastRowEmpty) ? 9 : 0));

		System.out.println("additional slots = "+this.additionalSlots+", lastItem: "+lastItem+", isFull: "+isFull+", isFullExceptLastRow: "+isFullExceptLastRow+", lastRowEmpty: "+lastRowEmpty); // TODO remove
	}

	@Override
	public void setAdditionalSlots(int additionalSlots) {
		// bound between 0 to config max
		additionalSlots = MathHelper.clamp(additionalSlots, 0, Config.maxExtraSlots);
		// must be multiple of 9
		if (additionalSlots % 9 != 0)
			additionalSlots = additionalSlots + (9 - additionalSlots % 9);

		// update main size
		while (main.size() < 36 + additionalSlots)
			main.add(ItemStack.EMPTY);
		while (main.size() > 36 + additionalSlots)
			main.remove(main.size() - 1);

		this.additionalSlots = additionalSlots;
	}

	@Inject(method = "updateItems", at = @At("TAIL"))
	public void updateItems(CallbackInfo ci) {
		// update infinitory size
		if (this.needToUpdateInfinitorySize) {
			this.updateInfinitorySize();
			this.needToUpdateInfinitorySize = false;
		}
		// TEST add extra slots if needed
		((IScreenHandler)((PlayerInventory)(Object)this).player.playerScreenHandler).updateExtraSlots();
		if (((PlayerInventory)(Object)this).player.currentScreenHandler != null)
			((IScreenHandler)((PlayerInventory)(Object)this).player.currentScreenHandler).updateExtraSlots();
		// sort
		if (this.needToSort) {
			this.sort();
			this.needToSort = false;
		}
		// update client (needs to be sent twice for some reason)
		if (this.needToUpdateClient > 0/* && ((PlayerInventory)(Object)this).player.age > 10*/) {
			((PlayerInventory)(Object)this).player.playerScreenHandler.updateToClient();
			--this.needToUpdateClient;
		}

		// TODO remove
		/*if (((PlayerInventory)(Object)this).player.age % 50 == 0) {
			ScreenHandler handler = ((PlayerInventory)(Object)this).player.currentScreenHandler;
			List<Slot> slots = handler.slots;
			String str = slots.size()+"";
			for (int i=0; i<slots.size(); ++i)
				System.out.println("(i:"+i+"):"+(slots.get(i) instanceof InfinitorySlot ? slots.get(i) : "["+slots.get(i)+",id:"+slots.get(i).id+",index:"+slots.get(i).getIndex()+",stack:"+slots.get(i).getStack()+",x:"+slots.get(i).x+",y:"+slots.get(i).y+"] "+slots.get(i).inventory)+",");
			System.out.println("(Current) "+handler.getClass().getSimpleName()+": ");
			System.out.println(" - slots: "+str);
			System.out.println(" - infinitorySlots: "+((IScreenHandler)handler).getInfinitorySlots().size()+((IScreenHandler)handler).getInfinitorySlots());

			handler = MinecraftClient.getInstance().currentScreen instanceof HandledScreen ? ((HandledScreen)MinecraftClient.getInstance().currentScreen).getScreenHandler() : null;
			if (handler != null) {
				slots = ((SlotDefaultedList)handler.slots).delegate;
				str = slots.size()+"";
				for (int i=0; i<slots.size(); ++i)
					System.out.println("(i:"+i+"):"+(slots.get(i) instanceof InfinitorySlot ? slots.get(i) : "["+slots.get(i)+",id:"+slots.get(i).id+",index:"+slots.get(i).getIndex()+",stack:"+slots.get(i).getStack()+",x:"+slots.get(i).x+",y:"+slots.get(i).y+"] "+slots.get(i).inventory)+",");
				System.out.println("(Client) "+handler.getClass().getSimpleName()+": ");
				System.out.println(" - slots: "+str);
				System.out.println(" - infinitorySlots: "+((IScreenHandler)handler).getInfinitorySlots().size()+((IScreenHandler)handler).getInfinitorySlots());
			}

			handler = ((PlayerInventory)(Object)this).player.playerScreenHandler;
			slots = handler.slots;
			str = slots.size()+"";
			for (int i=0; i<slots.size(); ++i)
				System.out.println("(i:"+i+"):"+(slots.get(i) instanceof InfinitorySlot ? slots.get(i) : "["+slots.get(i)+",id:"+slots.get(i).id+",index:"+slots.get(i).getIndex()+",stack:"+slots.get(i).getStack()+",x:"+slots.get(i).x+",y:"+slots.get(i).y+"] "+slots.get(i).inventory)+",");
			System.out.println("(PlayerScreenHandler) "+handler.getClass().getSimpleName()+": ");
			System.out.println(" - slots: "+str);
			System.out.println(" - infinitorySlots: "+((IScreenHandler)handler).getInfinitorySlots().size()+((IScreenHandler)handler).getInfinitorySlots());

			System.out.println("main: "+this.main.size()+this.main);
		}*/
	}

	@Unique
	@Override
	public void needToUpdateInfinitorySize() {
		this.needToUpdateInfinitorySize = true;
	}

	@Unique
	@Override
	public void needToUpdateClient() {
		if (!((PlayerInventory)(Object)this).player.world.isClient)
			this.needToUpdateClient = 1;
	}

	@Unique
	@Override
	public int getAdditionalSlots() {
		return this.additionalSlots;
	}

	@Override
	public int getMaxCountPerStack() {
		return Config.maxStackSize;
	}

	// ========== DROPS ON DEATH ==========

	/**Drop different items depending on config option*/
	@Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
	public void dropAll(CallbackInfo ci) {
		// up to stack of everything
		if (Config.dropsOnDeath == 1) {
			for (List<ItemStack> list : this.combinedInventory) 
				for (ItemStack stack : list) 
					if (!stack.isEmpty()) 
						((PlayerInventory)(Object)this).player.dropItem(stack.split(stack.getMaxCount()), true, false);
			ci.cancel();
		}
		// up to stack of hotbar and armor
		else if (Config.dropsOnDeath == 2) {
			List<ItemStack> list = Lists.newArrayList();
			list.addAll(this.offHand);
			list.addAll(this.armor);
			list.addAll(this.main.subList(0, 9));
			for (ItemStack stack : list) 
				if (!stack.isEmpty()) 
					((PlayerInventory)(Object)this).player.dropItem(stack.split(stack.getMaxCount()), true, false);
			ci.cancel();
		}
	}

	// ========== EXPANDING STACK SIZE ==========

	/**Have getOccupiedSlotWithRoomForStack check infinitory if it can't find a slot in main*/
	@Inject(method = "getOccupiedSlotWithRoomForStack", at = @At("RETURN"), cancellable = true)
	public void getOccupiedSlotWithRoomForStack(ItemStack stack, CallbackInfoReturnable<Integer> ci) {
		/*if (ci.getReturnValue() == -1) 
			for(int i = 0; i < this.infinitory.size(); ++i) 
				if (this.canStackAddMore((ItemStack)this.infinitory.get(i), stack)) 
					ci.setReturnValue(this.main.size() + 5 + i);*/
	}

	/**Have getEmptySlot check infinitory if it can't find an empty slot in main*/
	@Inject(method = "getEmptySlot", at = @At("RETURN"), cancellable = true)
	public void getEmptySlot(CallbackInfoReturnable<Integer> ci) {
		/*if (ci.getReturnValue() == -1) 
			for(int i = this.infinitory.size()-1; i >= 0 && i < this.infinitory.size(); --i) 
				if (((ItemStack)this.infinitory.get(i)).isEmpty()) 
					ci.setReturnValue(this.main.size() + 5 + i);*/
	}

	/**Remove a lot of restrictions on adding more to stack*/
	@Inject(method = "canStackAddMore", at = @At("RETURN"), cancellable = true)
	public void canStackAddMore(ItemStack existingStack, ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
		ci.setReturnValue(canStackAddMore(existingStack, stack));
	}

	@Unique
	private boolean canStackAddMore(ItemStack existingStack, ItemStack stack) {
		return !existingStack.isEmpty() && ItemStack.canCombine(existingStack, stack);
	}

	/**Restrict Ctrl+Q outside of inventory to max stack size*/
	@Redirect(method = "dropSelectedItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;removeStack(II)Lnet/minecraft/item/ItemStack;"))
	public ItemStack removeStack(PlayerInventory inventory, int slot, int amount) {
		return inventory.removeStack(slot, Math.min(amount, 64)); // get item max count instead of just 64? kinda complex to get itemstack...
	}

	@Redirect(method = "offer", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I"))
	public int getMaxCountOffer(ItemStack stack) {
		return Config.maxStackSize;
	}

	@Redirect(method = "addStack(ILnet/minecraft/item/ItemStack;)I", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I"))
	public int getMaxCountAddStack(ItemStack stack) {
		return Config.maxStackSize;
	}

	@Inject(method = "removeStack(II)Lnet/minecraft/item/ItemStack;", at = @At(value = "RETURN"))
	public void removeStackUpdateSize(int slot, int amount, CallbackInfoReturnable<ItemStack> ci) {
		// need to update size manually after this is called bc this calls ItemStack#splitStack which won't trigger an update with ListeningDefaultedList
		this.updateInfinitorySize();
	}

	// ========== NBT ==========

	@Inject(method = "readNbt", at = @At("RETURN"))
	public void readNbt(NbtList nbtList, CallbackInfo ci) {
		for(int i = 0; i < nbtList.size(); ++i) {
			NbtCompound nbtCompound = nbtList.getCompound(i);
			if (nbtCompound.contains("InfinitorySlot")) {
				int slot = nbtCompound.getInt("InfinitorySlot");
				ItemStack itemStack = ItemStack.fromNbt(nbtCompound);
				if (!itemStack.isEmpty()) {
					// increase main size if necessary
					while (slot > this.main.size()-1 && this.main.size() < Config.maxExtraSlots) 
						this.main.add(ItemStack.EMPTY);
					if (slot >= 0 && slot < this.main.size()) 
						this.main.set(slot, itemStack);
				}
			}
		}
		this.needToUpdateInfinitorySize();
		this.needToUpdateClient();
	}

	private DefaultedList<ItemStack> tempMain;

	@Inject(method = "writeNbt", at = @At("HEAD"))
	public void writeNbt1(NbtList nbtList, CallbackInfoReturnable<NbtCompound> ci) {
		// replace main with a copy that has normal size so extra slots aren't saved here
		this.tempMain = main;
		if (this.main.size() > 36)
			this.main = ((MainDefaultedList)main).subList(0, 36);
	}

	@Inject(method = "writeNbt", at = @At("RETURN"))
	public void writeNbt2(NbtList nbtList, CallbackInfoReturnable<NbtCompound> ci) {
		// change main back to normal
		this.main = this.tempMain;
		// write extra slots
		for(int i = 36; i < this.main.size(); ++i) {
			if (!((ItemStack)this.main.get(i)).isEmpty()) {
				NbtCompound nbt = new NbtCompound();
				nbt.putByte("Slot", (byte) 250); // put in a slot value that won't be read by vanilla
				nbt.putInt("InfinitorySlot", i);
				((ItemStack)this.main.get(i)).writeNbt(nbt);
				nbtList.add(nbt);
			}
		}
	}

}
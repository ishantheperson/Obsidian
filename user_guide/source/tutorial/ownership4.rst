Ownership -- Miscellaneous
============================

Ownership checks
-----------------

Ownership can be documented and checked by writing the ownership in brackets. For example, ``[m@Owned]`` indicates 
that ``m`` is an owning reference at that particular point in the code. The compiler will generate an error if this 
might not be the case. For example:


::

   // m is Owned initially but must be Unowned at the end.
   transaction spend(Money@Owned >> Unowned m) { 
       // implementation not shown
   }

   transaction testSpend(Money@Owned >> Unowned  m) {
      spend(m); [m@Unowned]; // OK
      [m@Owned]; // COMPILE ERROR: m is Unowned here
   }

Ownership checks in ``[]`` *never* change ownership; they only document and check what is known.


Getting rid of ownership
--------------------------
If ownership is no longer desired, ``disown`` can be used to discard ownership. For example:
::

   contract Money {
       int amount;

       transaction merge(Money@Owned >> Unowned mergeFrom) {
            amount = amount + mergeFrom.amount;
            [mergeFrom@Owned]; // As per declaration
            disown mergeFrom; 
            // We absorbed the value of mergeFrom, so we need to throw it away.
            // Since 'mergeFrom' is no longer an owning reference, 
            // it satisfies the 'Unowned' specification above
            // and we don't get an error about losing an owned asset.
       }
   }

*IMPORTANT*: `disown` is should be used *only* when you really want to throw something out. Above, `disown` is required because of the manual arithmetic used to manipulate `amount` inside the implementation of `Money`, but it is *not* needed in most normal code.

Invoking transactions
----------------------
 The compiler checks each invocation to make sure it is permitted according to the ownership of the transaction arguments. For example:

::

   transaction spend(Money@Owned >> Unowned m) {
      // implementation not shown
   };
   transaction print(Money@Unowned m) {
       // implementation not shown
   }

   transaction test() {
      Money m = ... // assume m is now owned.
      print(m); // m is still Owned
      spend(m); // m is now Unowned
      spend(m); // COMPILE ERROR: spend() requires Owned input, but m is Unowned here
   }


Handling Errors
-----------------
Errors can be flagged with ``revert``, which stops execution and discards all of the changes that have occurred since the outermost transaction started. A description of the error can be provided as well. An example of usage is given below.
::

   transaction checkAmount(Money@Unowned m) {
     if (m.getAmount() < 0) {
         revert("Money must have an amount greater than 0");
     }
   }

Return
--------------
Return statements affect the type of the returned expression according to the declared return type. For example:

::

   asset contract Candy {}

   contract VendingMachine {
      transaction dispenseCandy() returns Candy@Owned {
         Candy c = ... // Get an owned reference somehow
         return c; // Satisfies return type because ownership of c is transferred to the caller
         // No accidental loss of c here because c is no longer Owned
      }

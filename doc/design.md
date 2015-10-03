# Design document for positano

This document reflects what positano is aiming to become and it will
be used as inspiration and as a way to determine the direction of the
development effort.

## We tell stories to ourselves

When programmers try to read and understand code, they build a
narrative in their heads about the fate of entities as they travel
through the stack of calls. This becomes especially relevant when
debugging code, in which case we try to discern at which point the
actual narrative of what really happens deviates from the narrative of
what *should* be happening (and this is where the bug is).

Depending on the situation, there are different types of narratives
that programmers built in their minds, but in many cases the question
boils down to: "how did we end up with this value at this particular
point in the execution". The narrative is constructed by reading the
source code itself and by mentally ovelaying on top of it the actual
values that flow through the system by using debuggers, logging or
print statements to observe them. Good understanding of the system
comes after we have observed several sets of inputs flowing through
the system, as they activate various code paths, and we have mentally
superimposed all the different path on top of the code itself.

In most cases, the information that we get when stepping through the
code using a debugger or logging is ephemeral. If you will excuse a
mixed metaphor, the information is being drip fed to us and we fill in
the pieces of the puzzle. In many cases, we start stepping through the
code slowly only to discover that the set of inputs is not the one
that actually causes the bug we are looking for. Or in other cases,
the logging is in the wrong place and we waste time moving it around
(or making it more granular) and re-running until the bug becomes
apparent. It's a slow process, but essential when coming into contact
with code, our own or otherwise.

What's more, all the information that comes out of reading code is
ephemeral and unstructured.

#Puregame


##A pure, functional, concurrent implementation of the game "Bomber Man"

When I first started dabbling in functional programming, I quickly understood how pure functions could help reusibility, reduce complexity, and ovearll
be more pleasant to work with.  However, I did not immediately see how you could write a purely functional state heavy, concurrent application.  From
what I've read on  various forums, message boards, and twitter, this lack of knowledge on the subject seems somewhat widespread among beginners of functional programming.
I thought it might be helpful as both an excersie and for others to show that a very simple concurrent multiplayer game could be made pure and maintain it's concurrency *and* simplicity. 
To understand the code, I'm going to assume fluency in scala and understanding the for comprehension syntactic sugar.  Some passing familiarity with the functional concepts such as monads and/or functional IO 
woud be helpful too, but I'll link to some of my favorite blog posts on the subject .  

So, what is a "pure" functional program?  I'll define it to mean a program containing only [Referentially Transparent](http://en.wikipedia.org/wiki/Referential_transparency_(computer_science)) 
functions. But why would we want a program consisting solely of Referentially Transparent functions?  Instead of trying to explain the answer myself, I'll use the cannonical [Functional Programming in Scala](http://www.manning.com/bjarnason/)'s excellent explanation:

>[Referential Transparency] enables a very simple and natural mode of reasoning about program evaluation, called the substitution model. When expressions are referentially transparent, we can imagine that
>computation proceeds very much like we would solve an algebraic equation. We fully expand every part of an expression, replacing all variables with their referents, and then reduce 
>it to its simplest form. At each step we replace a term with an equivalent one; we say that computation proceeds by substituting equals for equals. In other words, RT enables 
>equational reasoning about programs.

To remain purely functional, it was helpful to focus on three different challenges.  The first is how we 'change' data (our program state) without modifying variables, etc.
The second challenge was dealing with concurrency in a functional way, and finally, performing IO so we can interact with the real world in a pure way. 

To start, we need to define our 'entities', the data the entire program will be working with. 

```scala
case class PlayerStats(bombsThrown: Int, wounds: Int, playersHit: Int)

case class Player(name: String, callback: String, position: Int, stats: PlayerStats)

//there are better datastructures for what we are doing here, 
//but now this is conceptually pretty simple

case class GameBoard(players: Map[String, Player], bombs: Map[Int, Boolean])
```

Nothing surprising here, we have a player, a group of player statistics, and a way to represent all of the active players along with
where there are currently bombs.  This will represent the world view of the game.  Because we're using immutable data, we need a way to 
update the state of the world without mutation. 

##Lenses

Scala gives us free 'copy' methods for each case class, but we'd end up needing to do a lot of this:
```scala
gameBoard.copy(players = gameBoard.players.updated("playername", 
    gameboard.players(""playername").copy(stats = player.playerStats.copy(wounds = player.playerStats.wounds + 1))))
```

which is just soul crushing to write.  Instead we'll use something called a Lens.  These are in ScalaZ (and there are a few other libraries), but 
for simplicities' sake I'll actually write my own implementation.  First, a lens is simply a datastructure consisting of a "getter" and "setter".

```scala
case class VLens[A, B](set: (A, B) => A, get: A => B)
```

And we'll need to create instances for each field we want to update in our PlayerStats entity.  Our "bomb lens", for example,  takes a PlayerStats instance, an Int, and returns a new PlayerStats
instance.   
```scala
val bombLens = VLens.lensu[PlayerStats, Int]((stats, thrown) => stats.copy(bombsThrown = thrown), _.bombsThrown)
val bombLens = VLens.lensu[PlayerStats, Int]((stats, thrown) => stats.copy(bombsThrown = thrown), _.bombsThrown)
val woundLens = VLens.lensu[PlayerStats, Int]((stats, nw) => stats.copy(wounds = nw), _.wounds)
val hitLens = VLens.lensu[PlayerStats, Int]((stats, nh) => stats.copy(playersHit = nh), _.playersHit)
```
We also need a way to set the PlayerStats entity *on* the Player entity, so 
```scala    
val statsLens = VLens.lensu[Player, PlayerStats]((player, ns) => player.copy(stats = ns), _.stats)
```
So how do we combine a Lens[PlayerStats,=>Int] so we can get a Lens[Player, Int](Int representing our individual field) ? We'll expand on our Lens implementation:
```scala
case class VLens[a, b](set: (a, b) => a, get: a => b) {
    def andthen[c](otherlens: VLens[b, c]): VLens[a, c] = {
        VLens[a, c]((a, c) => {
            set(a, otherlens.set(get(a), c))
        }, (a) => otherlens.get(get(a)))
    }
}
```

Now we can compose our 'stats' lenses with our PlayerLens to create additional Lenses. 

```scala
val woundStatsLens = statsLens.andThen(woundLens) //Gives us a VLens[Player, Int]
val hitStatsLens = statsLens.andThen(hitLens)
val bombStatsLens = statsLens.andThen(bombLens)
```

Ok, we're making progress, but now how do we set a player (and subsequently, and of its relevant lenses) on a GameBoard? There are a few different fancy ways, but I think it's
simple and effective enough to use a function that takes a 'player' and returns a Lens.  Then we can continue composing our lenses

```scala
                  //k here is the player name, how we pull out the player from our GameBoard structure
val playerLens = (k: String) => VLens.lensu[GameBoard, Player]((gb, pl) => gb.copy(players = gb.players.updated(k, pl)), _.players(k))
val bombStatsPlayerLens = (k: String) => (playerLens(k).andThen(bombStatsLens))
```
and so on for the rest of the stats and player fields. 

##Actions

Now that we have our entities, along with ways to 'modify' them in a functional way,  let's come up with various actions a player can take:

```scala
  sealed trait Action

  case class Join(name: String, callback: String) extends Action
  case class Move(name: String, position: Int) extends Action
  case class BombPlaced(name: String, position: Int) extends Action //name is the user who placed it
  case class BombExplodes(name: String, position: Int) extends Action //name is the user who placed it
```


Again, very straight forward stuff.  Reacting to actions such as Joining, Moving, and placing a bomb is just going to be calling into our lenses with the world state
and the values passed in from the Action we received.  We need a bit more logic to determine our 'blast radious' for when a bomb finally explodes, so I just came up with something quick.  Note there is nothing 'special' 
about our game logic here, other than the fact that these are side effect free (Referentially Transparent) functions. 

```scala
  def getHitPlayers(p: Int, board: GameBoard): List[Player] = {
    val blastArea = getBlastArea(p, 10)
    board.players
      .map(_._2)
      .filter(p => blastArea.contains(p.position))
      .toList

  }

  def getUntilEdge(pos: Int, size: Int, ctr: Int, chk: (Int, Int) => Int, f: (Int, Int) => Int): List[Int] =
    if (chk(pos, ctr) % size == 0)
      List[Int]()
    else
      List[Int](f(pos, ctr)) ::: getUntilEdge(pos, size, ctr + 1, chk, f)

  def getBlastArea(pos: Int, size: Int): List[Int] = {
    val right = getUntilEdge(pos, size, 0, (_ + _), (_ + _))
    val left = getUntilEdge(pos, size, 0, (_ - _), (_ - _))
    val up = getUntilEdge(pos, size, 0, (_ + _), (a, b) => a + (b * size))
    val down = getUntilEdge(pos, size, 0, (_ - _), (a, b) => a - (b * size))
    right ::: left ::: up ::: down
  }
```

So how does our app work in aggregate?  Basically, we're going to get 'actions' coming in, update the global world state, and push
the global world state out to any players.  

```scala
    var world = GameBoard(players = Map[String,Player](), bombs=Map[Int, Boolean]()) //global in our example
```

We could code the 'move' action handler to look like the following

```scala
    case m: Move=> world = positionPlayerLens(m.name).set(world, m.position)
```
Which is fairly similar to how we're actually doing it, excep there's a problem.  Our game is concurrent. What happens if the world variable representing our Game State was
updated by another thread between reading it and modifing it?  We may have just wiped out someone else's changes, or vice versa.  Let's eliminate our global 'var' by replacing
it with the indominatable java.util.atomic.AtomicReference.  

```scala

    val worldRef = AtomicReference[GameBoard]()

    def handleMoveAtomic(m: Move): Unit = {
       val w = worldRef.get()
       val result = positionPlayerLens(m.name).set(w, m.position)
       if (!worldRef.compareAndSet(w, result)) //trying to atomically update
            handleMoveAtomic(m) //didn't work, so we'll try again
    }
```

So we have a way to ensure our update to the world state doesn't overwrite someone else's updates.  However, consider the case where we want to update mutiple times. 
An example of this is when we increment one player's wound count, and correspondingly increment the scoring player's hit count. 

```scala
    world = woundStatsPlayerLens("bob").set(world, 1)
    world = hitStatsPlayerLens("alice").set(world, 1) 
```
What we need is transactional semantics for a set of actions.  We could try and code a bunch of methods interwoven with AtomicReference compare and sets, but that's 
pretty awful and most likely error prone. 


##State Monad
First, let's talk a bit about some magic called the State Monad.
Instead of going on about Mexican food or Astronauts, I'll just show the code, implore you to read it over a *lot*, (if you are unfamiliar), and
then send you off to the [tutorial that helped me understand it](http://blog.tmorris.net/posts/the-state-monad-for-scala-users/index.html).  Coming from an imperative 
background, I did have to spend some time thinking about the example, and even typing it and play with it.  The State Monad is also excellently covered in 
chapter six of Functional Programming in Scala.  

```scala
    case class State[A,B](run: A=>(A,B)) {
        def map[C](fmap: B=>C) = 
            State[A,C](a => {
                val (na,b) = run(a)
                val c = fmap(b)
                (na,c)
            })

        def flatMap[C](fmap: B=>State[A,C]) = 
            State[A,C](a => {
                val (na,b) = run(a)
                fmap(b).run(na)
            })
    }
```


So, why is the State Monad so powerful?   Because it allows us to compose multiple state changes into a single state transition.  There
are plenty of ways to compsose functions or actions, but State has the additional benefit of the fact that you can get a State from a Lens.


We'll add one more method to our Lens implemenation along wtih an alias.  ScalaZ and most Lens impelmenations will have a way to get a State monad. 

```scala
case class VLens[a, b](set: (a, b) => a, get: a => b) {

  def mods(b: B) = State[A, B](a => {
    (set(a, b), b)
  })

  def :=(b: B) = mods(b)

  def andthen[c](otherlens: vlens[b, c]): vlens[a, c] = {
        vlens[a, c]((a, c) => {
            set(a, otherlens.set(get(a), c))
        }, (a) => otherlens.get(get(a)))
  }
}

```

And because of scala's for comprehension, along wtih the fact that the State Monad has flatMap and Map, we now we can do nifty things like:
```scala
    val s = 
    for {
        _ <- woundStatsPlayerLens("bob") mods +1
        _ <- hitStatsPlayerLens("alice") mods +1
    } yield ()

    //s is scalaz.IndexedStateT[scalaz.Id.Id,puregame.Data.GameBoard,puregame.Data.GameBoard,Unit]
    // which in our case is equivalent to State[GambeBoard, Unit]
```

##Concurrency

Since in the code I'm using ScalaZ's State monad, we actually end up getting a more flexible and powerful version of the State Monad (IndexedStateT), but
for our purposes it works exactly like the one I described.  Once we have a State Monad that describes how we're updating the GameBoard, we need a way to
execute it.  We'll create a structure that holds an internal AtomicReference[A], and has a method called 'commit' which will handle 'running' a State[A,_] we 
pass to it.  
```scala
sealed trait AtomicSTRef[A] {

  def aref: AtomicReference[A]

  def commit[B](s: State[A, B]): IO[(A, B)] = {
    val m = aref.get()
    val result = s(m)
    if (!aref.compareAndSet(m, result._1))
      commit(s)
    else
      IO { result }
  }

  def frozen = IO { aref.get }

}
```
We're using the State Monad in combination with an AtomicReference to ensure a *batch* of State changes to the GameBoard can run 
unimpeded by another thread.  If another thread modifies the AtomicReference by calling commit with a batch of changes while the original thread is running, 
it simply retries the *same* State changes to a new version of the GameBoard.  We now have transacitonal semantics for State changes!  



You may notice that commit returns IO, which isn't something I've explaiend yet...

##IO

IO is another crucial monad that lets us maintain Referntial Transparency while interacting with the outside world.  To quote Runar Bjarnason again, 
>Instead of running I/O effects everywhere in our code, we build programs through the IO DSL, compose them like ordinary values, and then run them
>with unsafePerformIO as part of our main.









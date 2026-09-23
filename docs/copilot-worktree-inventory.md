# Copilot worktree inventory

Recorded 2026-09-23 from `C:\Users\radch\.copilot\repos\copilot-worktrees`.

## Source of truth

For `rdp-uav-addon`, the intended source of truth is the active checkout at
`D:\Forge Modding\rdp-uav-addon` (`main`, HEAD
`0efcc57234de125454e842cf9ff106d252587ff2`). The Copilot worktree listed below
is a separate task branch and is **not** the source of truth. This designation
is an operational assumption based on the active project opened for this task;
the user can revise it.

For other projects in this Copilot worktree collection, this inventory does
not designate any source of truth. Their rows are marked **No**. A common
ancestor is recorded as the base revision, using the local `main` ref (or
`origin/main` when that was the available default ref). `Unknown` means there
was no resolvable common ancestor or commit. These are local Git refs and may
not reflect a remote's current state.

## Worktrees

| Worktree | Branch | Base revision | HEAD | Dirty | Intended source of truth? |
|---|---|---|---|---|---|
| `minecraft/lavashi-coder-fictional-couscous` | Unborn | Unknown | None | No | No |
| `minecraft/lavashi-coder-glowing-telegram` | Unborn | Unknown | None | No | No |
| `minecraft/rottenmoon23-ideal-enigma` | `rottenmoon23-uav-mission-system` | Unknown | `a64e60ef25d93bebdb15f329c1cfbaeebae126e9` | Yes | No |
| `RDP Core/begletssd-ideal-winner` | `begletssd-rdp-core-post-rework-audit` | `d4d0119334` | `583c38d018bfe13564b4ab38c0dcda3318485390` | No | No |
| `RDP Core/begletssd-psychic-spork` | `begletssd-rdp-client-distortion` | `d4d0119334` | `4ccdc314f50858b7b4aa6787270992eaca1cb936` | No | No |
| `RDP Core/gymbro54-fuzzy-pancake` | `gymbro54-bounded-mutation-queue` | `f3aeb68654` | `f3aeb6865491458be4770e0941577d19ff54becb` | Yes | No |
| `RDP Core/simon-official321-cuddly-potato` | `simon-official321-interdimensional-anomaly-core` | `906301d82e` | `c85016ad5591252c5d17d888054fdf0a2da01411` | No | No |
| `RDP Core/simon-official321-scaling-engine` | `simon-official321-resolve-anomaly-todos` | `906301d82e` | `863359200cc24b1a5897dd1f160191f142cc879b` | No | No |
| `RDP Core/skyphe123-effective-adventure` | `skyphe123-scp-controller-integration` | `66c05c62b3` | `3eef08651a6fbd1ea1c00aa4f1e8f4ea82b901be` | No | No |
| `RDP Core/skyphe123-verbose-journey` | `skyphe123-implement-scp-integration` | `66c05c62b3` | `23bfc1ab38b0e1a47ee9c11e112fcb0985d40b27` | No | No |
| `rdp-uav-addon/blast-svg-automatic-doodle` | `blast-svg-automatic-doodle` | `e7af172aac` | `e7af172aac9f772c5176693e89e4fbffe3221ac1` | Yes | **No** |
| `RDPChunkRewriter/gymbro54-ideal-funicular` | `gymbro54-fix-rdp-chunk-cascade` | `390ed835e5` | `390ed835e5875ce9ceaa232d1db41c62091e4b13` | Yes | No |
| `RDPChunkRewriter/gymbro54-studious-fiesta` | `gymbro54-studious-fiesta` | `390ed835e5` | `390ed835e5875ce9ceaa232d1db41c62091e4b13` | No | No |
| `RDPChunkRewriter/ladyladyovich23-glowing-doodle` | `ladyladyovich23-fix-rc-structure-crash` | `390ed835e5` | `b72ec2d27bbf5012735d07b9b507dd70813bb157` | Yes | No |
| `RDPChunkRewriter/ladyladyovich23-studious-pancake` | `ladyladyovich23-redeploy-bounded-rc-scheduler` | `390ed835e5` | `390ed835e5875ce9ceaa232d1db41c62091e4b13` | Yes | No |
| `RDPChunkRewriter/rottenmoon23-psychic-fishstick` | `rottenmoon23-rdp-anomaly-overhaul` | `e73fed6d3a` | `e73fed6d3ab7bc26bdcb8ad0e5d9118f6e9eb74b` | Yes | No |
| `RDPChunkRewriter/rottenmoon23-symmetrical-tribble` | `rottenmoon23-anomaly-engine-rework` | `e73fed6d3a` | `e73fed6d3ab7bc26bdcb8ad0e5d9118f6e9eb74b` | Yes | No |
| `RDPChunkRewriter/simon-official321-didactic-sniffle` | `simon-official321-didactic-sniffle` | `e73fed6d3a` | `e73fed6d3ab7bc26bdcb8ad0e5d9118f6e9eb74b` | No | No |
| `RDPChunkRewriter/skyphe123-automatic-goggles` | `skyphe123-plan-rdp-core-integration` | `e73fed6d3a` | `257a1a43d6c9b6dce031ad2bd2fab3b1d852407d` | No | No |
| `RDPMixins/simon-official321-solid-engine` | `simon-official321-fix-intellij-gradle-setup` | `01857514ec` | `01857514ec7e2574d7b1c35d86231d4ad830e9da` | Yes | No |
| `scp controller/skyphe123-upgraded-dollop` | `skyphe123-upgraded-dollop` | `9f9c85971a` | `9f9c85971ac9efe481e258afa6ef0454ddd26d1f` | No | No |
| `scp controller/skyphe123-upgraded-fortnight` | `skyphe123-harden-rdp-bridge` | `9f9c85971a` | `e044f604eb1b28888e97bf1c3d5a3241b35b9ccc` | Yes | No |

Dirty worktrees are intentionally left untouched. Re-run Git status before
restoring, deleting, or merging any of them; this inventory is a snapshot, not
a synchronization mechanism.

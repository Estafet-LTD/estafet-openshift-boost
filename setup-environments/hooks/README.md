# estafet-openshift-boost git hooks

A commit-msg git hook that checks whether the commit message contains a valid trello link and that the card's status != Done

### Requirements

* **On Windows:** Git
* **On Linux:** Curl

> Note: This has currently only been tested on Windows

### How to use
1.
* If you have checked out the top level project - e.g. *estafet-blockchain-demo* then copy the file commit-msg to the *git\estafet-blockchain-demo\.git\modules\sub_project_name\hooks* folder where *sub_project_name* is the name of the sub-project you will be commiting to and want the validation to work on
* If you have checked out a sub-project directly then copy the file commit-msg to the folder *git\sub_project_name\.git\hooks* where *sub_project_name* is the name of the sub-project you have checked out to work on and want validation on your commits
2. 
 * **On Windows:** Make sure the first line of thepoints to the *sh.exe* file included with Git. E.g. #!C:/Program\ Files/Git/usr/bin/sh.exe 
 * **On Linux:** Change the top line to #!/bin/sh
 3.
 * Commit via command line with a commit message containing a trello link E.g. ``` git commit -m "https://trello.com/c/8M8wss8T This is my commit message" ```
 > Note: It may not work when commiting via an IDE

### Testing on Windows in PowerShell
> Make sure you don't have any changes you don't want to loose on your branch!
* In the project you have set the commit-msg file up for, go to a location to commit a new file e.g. *main/resources*
* Create a new file with ```New-Item test.txt```
* Add the file ```git add test.txt```
* Commit the file ```git commit -m "your commit message here"```
If the commit-msg hook rejects the commit (because of wrong trello link etc you can run another commit with a different commit message again. If it succesfully commited but you want to revert it you can run ```git reset --hard HEAD^```, you will then need to go back to the first step of creating the file.

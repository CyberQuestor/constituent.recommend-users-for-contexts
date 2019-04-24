### Version
This pipeline is now **0.0.2**

_Target API version: **1.1.3.3**_

### Setup project

Follow skeleton project for setup instructions

### Deployment

This section provides you details on how to provision HMLP on to HOLU for this constituent. Do not forget to add access to event server as;

- Edit `/etc/default/haystack` and add base paths for event server at both announcer and consumer nodes.
    - `holu.base=http://192.168.136.90:7070`

#### Setup event pipeline
The first element is to generate access tokens denoted as prediction pipeline units.

- Execute the following to generate similar content unit
    - `pio app new constituent.recommend-users-for-contexts`
    - Add `--access-key` parameter if you want to control key generated
        - It should be a 64-char string of the form `abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ12`
- Record unit ID and access key. You will need this later.

#### Prepare constituent
It is time to prepare constituent unit files that eventually manifests as a HML pipeline.

- Retrieve engine files by cloning relevant git repository
    - Setup the folder as `mkdir -p /var/lib/haystack/pio/constituents/constituent.recommend-users-for-contexts`
    - Go to folder as `cd /var/lib/haystack/pio/constituents/constituent.recommend-users-for-contexts`
    - Generate structure folders as; `mkdir bin conf pipeline`
    - Go to pipeline folder and get all files as
        - Either clone to pipeline as, `git clone git@repo.haystack.one:server.tachyon/constituent.recommend-users-for-contexts.git pipeline`
        - Or if it is zipped, `unzip constituent.recommend-users-for-contexts.zip -d pipeline`
        - Make sure that the application name is set right at; `pipeline/engine.json`. Change `appName` to `constituent.recommend-users-for-contexts`.
    - Copy all scripts to bin as; `cp pipeline/src/main/resources/scripts/* bin/`
    - Copy configuration to conf as; `cp pipeline/src/main/resources/configuration/* conf/`
    - Ensure that all scripts have execute permission as; `chmod +x bin/*`
    - Get your configuration right as; `vi conf/pipeline.conf`
        - Pay attention to `HOSTNAME, HOST, ACCESS_KEY, TRAIN_MASTER, DEPLOY_MASTER, X_CORES and Y_MEMORY`
- Edit `/etc/default/haystack` and add access keys to denote addition of HMLP.
    - For **consumer** nodes;
        - `haystack.tachyon.events.dispatch.userstocontext=<accesskey>`
- Complete events import through migration and turning on concomitant consumer

#### Initiate first time training and deploy
It is important to complete at least one iteration of build, train and deploy cycle prior to consumption.

- Go to folder as `cd /var/lib/haystack/pio/constituents/constituent.recommend-users-for-contexts/bin`
- Build the prediction unit as,
    - `./build`
- Train the predictive model as (ensure events migration is complete),
    - `./train`
- Deploy the prediction unit as,
    - `./deploy`
    - Do not kill the deployed process. Subsequent train and deploy would take care of provisioning it again.
    - You can verify deployed HMLP by visiting `http://192.168.136.90:17074/` and querying at `http://192.168.136.90:17074/queries.json `
- Edit `/etc/default/haystack` and add url keys to denote addition of HMLP.
- For **announcer** nodes;
    - `haystack.tachyon.pipeline.access.userstocontext=http://192.168.136.90:17074`

#### Setup consecutive training and deploy
Now that we have successfully provisioned this HMLP; let us set it up for a periodic train-deploy cycle. Note that events are always consumed at real-time but are not accounted for until the next train cycle builds the model.

- Find the accompanying shell scripts of constituent and modify for consumption.
    - Go to constituent directory at;
        - `cd /var/lib/haystack/pio/constituents/constituent.recommend-users-for-contexts/`
    - Verify configuration is right as; `vi conf/pipeline.conf`
        - Adjust spark driver and executor settings as required
    - Do not forget to make scripts executable;
        - `chmod +x bin/*`
    - Ensure `pio build` is run at least once before enabling `cron` job.

Finally, setup crontab for executing these scripts. `mailutils` is used in this script. For Ubuntu, you can do `sudo update-alternatives --config mailx` and see if `/usr/bin/mail.mailutils` is selected.

- Edit crontab file as;
    - `crontab -e` for user level
    - Add the entry as;
        - `0 2,8,14,20 * * * /var/lib/haystack/pio/constituents/constituent.recommend-users-for-contexts/bin/redeploy >/dev/null 2>/dev/null`
        - User `man cron` to check usage
        - Manage schedules in conjunction with all other HMLPs and ensure that trains do not overlap
    - Reload to take effect (optional)
        - `sudo service cron reload`
        - Restart if needed; `sudo systemctl restart cron`

You are all set!
